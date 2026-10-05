import scala.io.Source
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.shell.ReplReporterImpl
import java.io.{ByteArrayOutputStream, PrintStream, PrintWriter, File}

object TextScala {

  def parseHeader(headerLine: String): (Boolean, Boolean, Boolean) = {
    // Returns (isScalaBlock, echo, results)
    val trimmed = headerLine.stripPrefix("```").trim.toLowerCase
    val tokens = trimmed.split("\\s+").filter(_.nonEmpty)
    
    // Check if block specifies another language (e.g., ```python, ```bash)
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

  def processInlineCode(line: String, interpreter: IMain): String = {
    val pieces = splitByDoubleBackticks(line)
    if (pieces.length % 2 == 0) {
      // Unmatched double backticks: leave line untouched
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

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      Console.err.println("Usage: scala markdown.scala <input.msc> [output.md]")
      sys.exit(1)
    }

    val inputFilename = args(0)
    val outWriter: PrintWriter = if (args.length >= 2) {
      new PrintWriter(new File(args(1)))
    } else {
      new PrintWriter(Console.out)
    }

    val settings = new Settings
    settings.usejavacp.value = true
    val reporter = new ReplReporterImpl(settings)
    val interpreter = new IMain(settings, reporter)

    val codeDelimiter = "```"
    var isInsideCodeBlock = false
    var isCurrentScalaBlock = false
    var showCode = true
    var showResults = true
    var rawFenceLine = ""
    val codeBuffer = new StringBuilder

    val source = Source.fromFile(inputFilename)
    try {
      for (line <- source.getLines()) {
        if (line.startsWith(codeDelimiter)) {
          if (isInsideCodeBlock) {
            // Closing fence
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
                outWriter.println(s"${codeDelimiter}scala")
                outWriter.print(code)
                if (!code.endsWith("\n")) outWriter.println()
                outWriter.println(codeDelimiter)
              }

              if (showCode && showResults && output.nonEmpty) {
                outWriter.println()
              }

              if (showResults && output.nonEmpty) {
                outWriter.println(s"${codeDelimiter}scala")
                outWriter.println(output)
                outWriter.println(codeDelimiter)
              }
            } else {
              // Non-Scala block, pass through as-is
              outWriter.println(line)
            }

            codeBuffer.clear()
            isInsideCodeBlock = false
            isCurrentScalaBlock = false
            rawFenceLine = ""
          } else {
            // Opening fence
            val (isScala, echo, results) = parseHeader(line)
            isInsideCodeBlock = true
            isCurrentScalaBlock = isScala
            showCode = echo
            showResults = results
            rawFenceLine = line

            if (!isScala) {
              outWriter.println(line)
            }
          }
        } else {
          if (isInsideCodeBlock) {
            if (isCurrentScalaBlock) {
              codeBuffer.append(line).append("\n")
            } else {
              outWriter.println(line)
            }
          } else {
            outWriter.println(processInlineCode(line, interpreter))
          }
        }
      }
    } finally {
      source.close()
      outWriter.flush()
      if (args.length >= 2) {
        outWriter.close()
      }
    }
  }
}
