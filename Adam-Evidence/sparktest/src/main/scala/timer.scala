object timer {
  import breeze.linalg.Vector.castFunc
  import breeze.linalg.{sum}
  import org.apache.log4j.{Level, Logger}
  import org.apache.spark.sql._
  import org.apache.spark.sql.catalyst.dsl
  import org.apache.spark.sql.catalyst.dsl.expressions.{DslExpression, StringToAttributeConversionHelper}
  import org.apache.spark.sql.expressions.scalalang.typed
  import org.apache.spark.sql.functions.{array, col, count, explode, lit}
  import org.apache.spark.sql.types.{StringType, StructField, StructType}
  import org.graphframes.GraphFrame.{DST, ID, SRC}
  import org.graphframes.lib.AggregateMessages
  import org.graphframes.lib.Pregel.msg.isin
  import org.graphframes.GraphFrame

  import scala.collection.JavaConversions.collectionAsScalaIterable

    def main(): Unit = {

      import org.apache.spark.{SparkConf, SparkContext, sql}
      import scala.math._
      // tried to manipulate graphx but easier to expand edges
      Logger.getLogger("org").setLevel(Level.ERROR)
      val spark = SparkSession.builder.master("local").appName("Sparktest").getOrCreate()
      spark.sparkContext.setCheckpointDir("Checkpoint")
      import spark.implicits._
      for (n <- List("10","32","100","316","1000","3162","10000"); p <- List("1","2")){
        var name = "G_ " +  n + " _p" + p
        var edges = spark.read.option("header","true").csv("../../Data/"+name).toDF().select("V1","V2").withColumnRenamed("V1","src").withColumnRenamed("V2","dst")
        var e = edges.toDF(Seq("src","dst"): _*)
        var num = 1 to n.toInt
        var v = num.toDF("id")
        var g=GraphFrame(v,e)
        var t1_0 = System.nanoTime
        var cc = g.connectedComponents.run()
        //cc.show()
        var duration_0 = (System.nanoTime - t1_0) / 1e9d
        println("duration_0-" + n + "_" + p)
        println(duration_0)

        var t1_1 = System.nanoTime
        var pr = g.pageRank.tol(0.01).run()
        //pr.edges.show()
        var duration_1 = (System.nanoTime - t1_1) / 1e9d
        println("duration_1-" + n + "_" + p)
        println(duration_1)
        println("")
      }

    }



}
