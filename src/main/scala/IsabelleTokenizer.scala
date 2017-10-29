package info.hupel.isabelle.pmd

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.Properties

import scala.concurrent.duration._
import info.hupel.isabelle.{Model, Operation, Region, Regions, Reports, System}
import info.hupel.isabelle.api.{Configuration, Environment, Version}
import info.hupel.isabelle.setup.{Resources, Setup}
import net.sourceforge.pmd.cpd.{SourceCode, TokenEntry, Tokenizer, Tokens}
import org.log4s._
import monix.execution.Scheduler.Implicits.global
import shapeless.tag

import scala.concurrent.Await
import scala.io.Source

object IsabelleTokenizer {

  def tokenize(regions: Regions, env: Environment, file: String): List[TokenEntry] = {
    def mkTokenEntry(str: String, iter: CodepointIterator): Option[TokenEntry] =
      if (str.trim.isEmpty)
        None
      else
        Some(new TokenEntry(str.trim, file, 0))

    def tokenizeRegion(region: Region, iter: CodepointIterator): (List[TokenEntry], CodepointIterator) =
      tokenizeRegions(region.subRegions, iter, region.range.end)

    def tokenizeRegions(regions: Regions, iter: CodepointIterator, end: Int): (List[TokenEntry], CodepointIterator) = {
      def consume(iter: CodepointIterator, items: List[Region]): (List[TokenEntry], CodepointIterator) = items match {
        case Nil =>
          val (str, iter2) = iter.advanceUntil(end)
          (mkTokenEntry(str, iter).toList, iter2)
        case r :: rs =>
          val (prefix, iter2) = iter.advanceUntil(r.range.start)
          val (parts, iter3) = tokenizeRegion(r, iter2)
          val (rest, iter4) = consume(iter3, rs)
          (mkTokenEntry(prefix, iter).toList ::: parts ::: rest, iter4)
      }

      consume(iter, regions.items)
    }

    val content = env.decode(tag[Environment.Raw].apply(Source.fromFile(new File(file), "US-ASCII").mkString))
    val length = content.codePointCount(0, content.length - 1)
    val iter = CodepointIterator(content, 0, 1)

    tokenizeRegions(regions, iter, length)._1
  }

}

final class IsabelleTokenizer extends Tokenizer {

  private val logger = getLogger

  private var image: String = "HOL"
  private var version: String = "2017"

  def setProperties(properties: Properties): Unit = {
    image = properties.getProperty("isabelle.image", "HOL")
    version = properties.getProperty("isabelle.version", "2017")
  }

  private val resources = Resources.dumpIsabelleResources() match {
    case Left(err) =>
      logger.error("Could not dump resources.")
      logger.error(s"Reason: ${err.explain}")
      sys.error(err.explain)
    case Right(res) =>
      res
  }

  private val config = Configuration.simple(image)

  private lazy val env = {
    logger.info("Setting up Isabelle")
    Setup.default(Version.Stable(version), false) match {
      case Left(err) =>
        logger.error("Could not create setup.")
        logger.error(s"Reason: ${err.explain}")
        sys.error(err.explain)
      case Right(setup) =>
        setup.makeEnvironment(resources, Nil)
    }
  }

  private[pmd] lazy val system = env.flatMap(System.create(_, config))

  override def tokenize(sourceCode: SourceCode, tokenEntries: Tokens): Unit = {
    val path = Paths.get(sourceCode.getFileName).toRealPath()
    val future = for {
      e <- env
      s <- system
      r <- s.invoke(Operation.UseThys(Reports.empty)(_ + _, identity))(List(path.toString.stripSuffix(".thy")))
      model = r.unsafeGet.interpret(e)
      _ = println(model.pretty)
    } yield IsabelleTokenizer.tokenize(model.regions(path), e, sourceCode.getFileName)

    val result = Await.result(future, 10.seconds)

    result.foreach(tokenEntries.add)
    tokenEntries.add(TokenEntry.getEOF)

    result.foreach(entry => println(entry.getBeginLine))
  }

}