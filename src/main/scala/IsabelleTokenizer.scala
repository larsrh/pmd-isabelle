package info.hupel.isabelle.pmd

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

final class IsabelleTokenizer extends Tokenizer {

  private val logger = getLogger

  private var image: String = "HOL"
  private var version: String = "2017"
  private var ignoreIdentifiers: Boolean = false

  def setProperties(properties: Properties): Unit = {
    image = properties.getProperty("isabelle.image", "HOL")
    version = properties.getProperty("isabelle.version", "2017")
    ignoreIdentifiers = properties.getProperty(Tokenizer.IGNORE_IDENTIFIERS, "false").toBoolean
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

  private type Token = (String, Int)

  private def isIdentifier(region: Region): Boolean =
    region.markup.exists { case (markup, _) => markup == "free" }

  private def tokenize(regions: Regions, env: Environment, path: Path): List[Token] = {
    def mkTokenEntry(str: String, iter: CodepointIterator): Option[Token] =
      if (str.trim.isEmpty)
        None
      else
        Some((str.trim, iter.line))

    def tokenizeRegion(region: Region, iter: CodepointIterator): (List[Token], CodepointIterator) =
      if (ignoreIdentifiers && isIdentifier(region))
        (List(("IDENTIFIER", iter.line)), iter.advanceUntil(region.range.end)._2)
      else
        tokenizeRegions(region.subRegions, iter, region.range.end)

    def tokenizeRegions(regions: Regions, iter: CodepointIterator, end: Int): (List[Token], CodepointIterator) = {
      def consume(iter: CodepointIterator, items: List[Region]): (List[Token], CodepointIterator) = items match {
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

    val content = env.decode(tag[Environment.Raw].apply(Source.fromFile(path.toFile, "US-ASCII").mkString))
    val length = content.codePointCount(0, content.length - 1)
    val iter = CodepointIterator(content, 0, 1)

    tokenizeRegions(regions, iter, length)._1
  }

  override def tokenize(sourceCode: SourceCode, tokenEntries: Tokens): Unit = {
    val path = Paths.get(sourceCode.getFileName).toRealPath()
    val future = for {
      e <- env
      s <- system
      r <- s.invoke(Operation.UseThys(Reports.empty)(_ + _, identity))(List(path.toString.stripSuffix(".thy")))
      model = r.unsafeGet.interpret(e)
      _ = println(model.pretty)
    } yield tokenize(model.regions(path), e, path)

    val result = Await.result(future, 10.seconds)

    result.foreach(println)

    result.foreach { case (str, line) => tokenEntries.add(new TokenEntry(str, sourceCode.getFileName, line)) }
    tokenEntries.add(TokenEntry.getEOF)
  }

}