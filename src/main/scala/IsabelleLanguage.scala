package info.hupel.isabelle.pmd

import java.util.Properties
import monix.execution.Scheduler.Implicits.global
import net.sourceforge.pmd.cpd.AbstractLanguage
import scala.concurrent.Future

final class IsabelleLanguage(properties: Properties) extends AbstractLanguage("Isabelle", "isabelle", new IsabelleTokenizer, "thy") {

  def this() = this(System.getProperties)

  setProperties(properties)

  override def setProperties(properties: Properties): Unit = {
    getTokenizer.asInstanceOf[IsabelleTokenizer].setProperties(properties)
  }

  def dispose: Future[Unit] = {
    getTokenizer.asInstanceOf[IsabelleTokenizer].system.flatMap(_.dispose)
  }

}