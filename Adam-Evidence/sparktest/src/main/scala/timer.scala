object timer {
  import org.apache.log4j.{Level, Logger}
  import org.apache.spark.sql._
  import org.graphframes.GraphFrame

    def main(): Unit = {
      //reduces verbosity of output
      Logger.getLogger("org").setLevel(Level.ERROR)
      val spark = SparkSession.builder.master("local").appName("Sparktest").getOrCreate()
      spark.sparkContext.setCheckpointDir("Checkpoint")
      import spark.implicits._
      //for each generated graph, run the timing test.
      for (n <- List("10","32","100","316","1000","3162","10000"); p <- List("1","2")){
        var name = "G_ " +  n + " _p" + p
        var edges = spark.read.option("header","true").csv("../../Data/"+name).toDF().select("V1","V2").withColumnRenamed("V1","src").withColumnRenamed("V2","dst")
        var e = edges.toDF(Seq("src","dst"): _*)
        //create the vertex list (1 -> number of vertices in generated graph.
        var num = 1 to n.toInt
        var v = num.toDF("id")
        var g=GraphFrame(v,e)

        //run connected components tes
        var t1_0 = System.nanoTime
        var cc = g.connectedComponents.run()
        var duration_0 = (System.nanoTime - t1_0) / 1e9d
        println("duration_0-" + n + "_" + p)
        println(duration_0)

        //run page rank test
        var t1_1 = System.nanoTime
        var pr = g.pageRank.tol(0.01).run()
        var duration_1 = (System.nanoTime - t1_1) / 1e9d
        println("duration_1-" + n + "_" + p)
        println(duration_1)
        println("")
      }
      //the output for the tests were copied an put into a file called 'scala test' in the Data folder
    }
  }
