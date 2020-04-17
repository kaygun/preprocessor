import scala.io.Source.fromFile
import scala.tools.nsc._
import scala.reflect.internal.Reporter
import scala.tools.nsc.interpreter

object textScala {

  def main(args: Array[String]) {
    val settings = new Settings
    val eval = new Interpreter(settings)
    var flag = false
    val search = "```".r
    var buffer = ""
    val text = fromFile(args(0))
    text.getLines.foreach(line=>{
       if(search.findFirstIn(line).isEmpty) {
         println(line)
         if(flag) 
           buffer += line+"\n"
       }
       else {
         if(flag) {
           println("```\n\n```scala")
           eval.parse(buffer)
           println("```")
           buffer = ""
         }
         else
           println("```scala")
         flag = !flag
       }
    })
    text.close
  }
}
