object textScala {

  import scala.tools.nsc.Settings
  import scala.tools.nsc.interpreter.IMain
  import scala.io.Source.fromFile

  def main(args: Array[String]) {
    val settings = new Settings
    val eval = new IMain(settings)
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
           eval.interpret(buffer)
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
