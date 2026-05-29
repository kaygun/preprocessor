//> using scala 2.13
//> using dep org.scala-lang:scala-compiler:2.13.18

import scala.io.Source
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.shell.ReplReporterImpl

val filename = args(0)
val settings = new Settings
settings.usejavacp.value = true
val reporter = new ReplReporterImpl(settings)
val interpreter = new IMain(settings, reporter)

val codeDelimiter = "```"
var isInsideCodeBlock = false
var currentBlockType = ""
val codeBuffer = new StringBuilder

val source = Source.fromFile(filename)
try {
  for (line <- source.getLines()) {
    if (line.startsWith(codeDelimiter)) {
      if (isInsideCodeBlock) {
        // Closing delimiter
        if (currentBlockType == "scala") {
          println(s"$codeDelimiter\n\n${codeDelimiter}output")
          interpreter.interpret(codeBuffer.toString())
          println(codeDelimiter)
        } else {
          // Non-scala block: just close it as-is
          println(codeDelimiter)
        }
        codeBuffer.clear()
        currentBlockType = ""
        isInsideCodeBlock = false
      } else {
        // Opening delimiter: extract block type
        currentBlockType = line.stripPrefix(codeDelimiter).trim
        if (currentBlockType == "scala") {
          println(s"${codeDelimiter}scala")
        } else {
          println(line)
        }
        isInsideCodeBlock = true
      }
    } else {
      println(line)
      if (isInsideCodeBlock && currentBlockType == "scala") {
        codeBuffer.append(line).append("\n")
      }
    }
  }
} finally {
  source.close()
}
