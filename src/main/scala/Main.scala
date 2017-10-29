package info.hupel.isabelle.pmd

import java.io.File
import java.util.{Arrays, Iterator}
import scala.collection.JavaConverters._
import net.sourceforge.pmd.cpd._

object Main extends App {

  val conf = new CPDConfiguration()
  val lang = new IsabelleLanguage()

  conf.setFiles(Arrays.asList(new File(args(0))))
  conf.setMinimumTileSize(2)
  conf.setIgnoreIdentifiers(true)
  conf.setRenderer(new XMLRenderer())
  conf.setLanguage(lang)
  conf.postContruct()

  CPDConfiguration.setSystemProperties(conf)

  val cpd = new CPD(conf)
  CPDCommandLineInterface.addSourceFilesToCPD(cpd, conf)

  println("=== MATCHES ===")

  try {
    cpd.go()
    println(conf.getRenderer.render(cpd.getMatches))
  }
  finally {
    lang.dispose
  }

}