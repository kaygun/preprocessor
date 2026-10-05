//> using scala 2.13
//> using dep org.scala-lang:scala-compiler:2.13.18

import scala.io.Source
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.shell.ReplReporterImpl
import java.io.{ByteArrayOutputStream, PrintStream}

val filename = args(0)
val settings = new Settings
settings.usejavacp.value = true
val reporter = new ReplReporterImpl(settings)
val interpreter = new IMain(settings, reporter)

val codeDelimiter = "```"
var isInsideCodeBlock = false
var isCurrentScalaBlock = false
var showCode = true
var showResults = true
val codeBuffer = new StringBuilder

def parseHeader(headerLine: String): (Boolean, Boolean, Boolean) = {
  val trimmed = headerLine.stripPrefix("```").trim.toLowerCase
  val tokens = trimmed.split("\\s+").filter(_.nonEmpty)
  val isNonScala = tokens.exists(t => t != "scala" && t != "hide" && t != "all")
  if (isNonScala) {
    (false, true, false)
  } else {
    val hideAll = trimmed.contains("hide all")
    val hide = !hideAll && trimmed.contains("hide")
    if (hideAll) (true, false, false)
    else if (hide) (true, false, true)
    else (true, true, true)
  }
}

def splitByDoubleBackticks(line: String): List[String] = {
  val pieces = scala.collection.mutable.ListBuffer[String]()
  var start = 0
  val delim = "``"
  while (start <= line.length) {
    val idx = line.indexOf(delim, start)
    if (idx >= 0) {
      pieces += line.substring(start, idx)
      start = idx + delim.length
    } else {
      pieces += line.substring(start)
      start = line.length + 1
    }
  }
  pieces.toList
}

def processInlineCode(line: String): String = {
  val pieces = splitByDoubleBackticks(line)
  if (pieces.length % 2 == 0) {
    line
  } else {
    val sb = new java.lang.StringBuilder()
    for ((piece, idx) <- pieces.zipWithIndex) {
      if (idx % 2 == 0) {
        sb.append(piece)
      } else {
        try {
          val varName = s"__inline_${System.nanoTime()}_"
          interpreter.quietRun(s"val $varName = ($piece)")
          val vOpt = interpreter.valueOfTerm(varName)
          sb.append(vOpt.map(_.toString).getOrElse(""))
        } catch {
          case e: Throwable => sb.append(s"[Error: ${e.getMessage}]")
        }
      }
    }
    sb.toString
  }
}

val source = Source.fromFile(filename)
try {
  for (line <- source.getLines()) {
    if (line.startsWith(codeDelimiter)) {
      if (isInsideCodeBlock) {
        if (isCurrentScalaBlock) {
          val code = codeBuffer.toString()
          val baos = new ByteArrayOutputStream()
          val ps = new PrintStream(baos, true, "UTF-8")
          Console.withOut(ps) {
            interpreter.interpret(code)
          }
          ps.flush()
          val rawOutput = baos.toString("UTF-8").trim
          val output = rawOutput.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]|\\[[0-9;]+m", "")

          if (showCode) {
            println(s"${codeDelimiter}scala")
            print(code)
            if (!code.endsWith("\n")) println()
            println(codeDelimiter)
          }

          if (showCode && showResults && output.nonEmpty) {
            println()
          }

          if (showResults && output.nonEmpty) {
            println(s"${codeDelimiter}scala")
            println(output)
            println(codeDelimiter)
          }
        } else {
          println(line)
        }

        codeBuffer.clear()
        isInsideCodeBlock = false
        isCurrentScalaBlock = false
      } else {
        val (isScala, echo, results) = parseHeader(line)
        isInsideCodeBlock = true
        isCurrentScalaBlock = isScala
        showCode = echo
        showResults = results
        if (!isScala) {
          println(line)
        }
      }
    } else {
      if (isInsideCodeBlock) {
        if (isCurrentScalaBlock) {
          codeBuffer.append(line).append("\n")
        } else {
          println(line)
        }
      } else {
        println(processInlineCode(line))
      }
    }
  }
} finally {
  source.close()
}
