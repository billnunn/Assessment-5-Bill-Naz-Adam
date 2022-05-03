import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql._
import org.apache.spark.sql.functions.{ col, lit}
import org.graphframes.lib.AggregateMessages
import org.graphframes.GraphFrame

import scala.collection.JavaConversions.collectionAsScalaIterable


object test {

  /* The following commented code was the original implementation of calculating the first approach to the spread of negative sentiment in the network.
  This was required as in the original data processing, rather than having edges repeated n times there was a column signifying the number of n.
  However I found that it was much easier to change the data processing and rewrite the first approach than try to implement the second approach using this data format.

    Score was used as a function for a map to calculate the new weight of a vertex for the first approach.
    def score(row: Row): Int = {
    val vs = row.where(v3.col("src") === row.getString(0))
    val vi = indeg.where(indeg.col("id") === row.getString(1))
    val vo = outdeg.where(outdeg.col("id") === row.getString(0))
    val s = vs.first().getInt(1) / (vi.first().getInt(1) * (vo.first().getInt(1)))
    s
  }

  calcoutdegree is an adapted version of the GraphFrames outDegree function to calculate the outdegree of the graph given in the original format.
  def calcoutdeg(g: GraphFrame) : DataFrame ={
    g.edges.groupBy(g.edges("src").as(ID)).agg(functions.sum("n").cast("int").as("outDegree"))
  }

  calcindegree is an adapted version of the GraphFrames inDegree function to calculate the indegree of the graph given in the original format.
  def calcindeg(g: GraphFrame): DataFrame= {
    g.edges.groupBy(g.edges(DST).as(ID)).agg(functions.sum("n").cast("int").as("inDegree"))
  }*/


  def main(args: Array[String]): Unit = {
    // in order to run the timer function for the time complexity analysis, uncomment the following line and comment out the rest of the main function
    //timer.main()

    //reduces verbosity of output
    Logger.getLogger("org").setLevel(Level.ERROR)
    //creates a spark session
    val spark = SparkSession.builder.master("local").appName("Sparktest").getOrCreate()
    spark.sparkContext.setCheckpointDir("Checkpoint")
    import spark.implicits._

    //from the input (a list of edges) format the edges into a DataFrame and create a DataFrame containing a list of the vertices.
    val edges = spark.read.option("header","true").csv("../../Data/edges.csv").toDF().withColumnRenamed("to","src").withColumnRenamed("from","dst")
    val e = edges.toDF(Seq("src","dst"): _*)
    val v=e.select("src").union(e.select("dst")).distinct().withColumnRenamed("src", "id")

    //extract the list of fired employees and convert the DataFrame to a Sequence
    val fired = spark.read.option("delimiter", ",").csv("../../Data/fired.csv")
    val fired1 = fired.select("_c0").map(x => x.getString(0)).collectAsList().toSeq

    //for those employees that were fired, set the neg_sentiment (negative sentiment) to 1
    //and for those who were not fired, set the neg_sentiment (negative sentiment) to 0
    val v1 = v.filter(v("id").isin(fired1: _*)).withColumn("neg_sentiment", lit(1))
    val v2 = v.filter(!v("id").isin(fired1: _*)).withColumn("neg_sentiment", lit(0))
    val v3 = v1.union(v2)

    //create a graph using the vertices with negative sentiment vertices and the edges from the input
    val g=GraphFrame(v3,e)

    //check if the graph is connected, this assigns a connected component number to each vertex, so if all the numbers are the same then they are all connected.
    val cc = g.connectedComponents.run()
    cc.describe("component").show()
    cc.write.csv("../../Data/connected.csv")

    //calculate the in-degrees and out-degrees of each vertex in g
    val indeg = g.inDegrees
    val outdeg = g.outDegrees

    ///////////////////////////// Approach 1 Round 1
    //create a DataFrame from the edge DataFrame and add columns corresponding to neg_sentiment, inDegree of dst and outDegree of src
    val e1 = e.join(v3, e("src") === v3("id"))
    val e2 = e1.join(indeg, e1("dst") === indeg("id")).select(e1("*"),indeg("inDegree"))
    val e3 = e2.join(outdeg, e2("src") === outdeg("id")).select(e2("*"),outdeg("outDegree"))

    //then using the DataFrame above, combine the values using the formula (for the first approach) to give a weight to each edge.
    val e4 = e3.map(x => (x.getString(0), x.getString(1), 1.0 * x.getInt(3) / x.getInt(4))).toDF("src", "dst", "weight")
    val g1 = GraphFrame(v3, e4)

    val AM = AggregateMessages

    //add the directed weights of the edges to the neg_sentiment value of the destination vertex. In Theory this is the dissemination of negative sentiment throughout the network.
    val msgToDst = AM.edge("weight")
    val agg = { g1.aggregateMessages
      .sendToDst(msgToDst)
      .agg(functions.sum(AM.msg).as("summedweight")) }
    val v4 = v3.join(agg, v3("id") === agg("id")).select(v3("id"), v3("neg_sentiment"), agg("summedweight"))
    val v5 = v4.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    //rec is a list of all vertices with neg_sentiment of 0 before this round of dissemination.
    val rec = v4.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6 = v3.filter(!v3("id").isin(rec: _*)).withColumn("totalweight", v3.col("neg_sentiment")).select("id","totalweight")
    val v7 = v6.union(v5).withColumnRenamed("totalweight","neg_sentiment")

////////////////////////////////////////////////////Approach 1 Round 2

    //create a DataFrame from the edge DataFrame and add columns corresponding to neg_sentiment after Round 1, inDegree of dst and outDegree of src
    val e1_1 = e.join(v7, e("src") === v7("id"))
    val e2_1 = e1_1.join(indeg, e1_1("dst") === indeg("id")).select(e1_1("*"),indeg("inDegree"))
    val e3_1 = e2_1.join(outdeg, e2_1("src") === outdeg("id")).select(e2_1("*"),outdeg("outDegree"))

    //then using the DataFrame above, combine the values using the formula (for the first approach) to give a weight to each edge.
    val e4_1 = e3_1.map(x => (x.getString(0), x.getString(1), 1.0 * x.getDouble(3) / x.getInt(4))).toDF("src", "dst", "weight")
    val g1_1 = GraphFrame(v7, e4_1)

    //add the directed weights of the edges to the neg_sentiment value of the destination vertex. In Theory this is the dissemination of negative sentiment throughout the network.
    val agg_1 = { g1_1.aggregateMessages
      .sendToDst(msgToDst)
      .agg(functions.sum(AM.msg).as("summedweight")) }
    val v4_1 = v7.join(agg_1, v7("id") === agg_1("id")).select(v7("id"), v7("neg_sentiment"), agg_1("summedweight"))
    val v5_1 = v4_1.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    //rec_1 is a list of all vertices with neg_sentiment of 0 before this round of dissemination.
    val rec_1 = v4_1.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6_1 = v7.filter(!v7("id").isin(rec_1: _*)).withColumn("totalweight", v7.col("neg_sentiment")).select("id","totalweight")
    val v7_1 = v6_1.union(v5_1).withColumnRenamed("totalweight","neg_sentiment")
    v7_1.describe("neg_sentiment")
    v7_1.write.csv("../../Data/sent1.csv")

//////////////////////////// Approach 2 Round 1

    //use the Page Rank algorithm to provide a weight to each edge and then add this column to the edge DataFrame already containing neg_sentiment, inDegree of dst and outDegree of src.
    val pr = g.pageRank.maxIter(5).run()
    val e4_1_1 = e3.join(pr.edges,(e3("src") === pr.edges("src")) && (e3("dst") === pr.edges("dst"))).select(e3("*"),pr.edges("weight"))

    //then using the DataFrame above, combine the values using the formula (for the second approach) to give a weight to each edge.
    val e5_1_1 = e4_1_1.map(x => (x.getString(0),x.getString(1),1.0 * x.getInt(3) * x.getDouble(6))).toDF("src", "dst", "weight")
    val g_1_1 = GraphFrame(v3,e5_1_1)

    //add the directed weights of the edges to the neg_sentiment value of the destination vertex. In Theory this is the dissemination of negative sentiment throughout the network.
    val agg_1_1 = { g_1_1.aggregateMessages
      .sendToDst(msgToDst)
      .agg(functions.sum(AM.msg).as("summedweight")) }
    val v4_1_1 = v3.join(agg_1_1, v3("id") === agg_1_1("id")).select(v3("id"), v3("neg_sentiment"), agg_1_1("summedweight"))
    val v5_1_1 = v4_1_1.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    //rec_1_1 is a list of all vertices with neg_sentiment of 0 before this round of dissemination.
    val rec_1_1 = v4_1_1.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6_1_1 = v3.filter(!v3("id").isin(rec_1_1: _*)).withColumn("totalweight", v3.col("neg_sentiment")).select("id","totalweight")
    val v7_1_1 = v6_1_1.union(v5_1_1).withColumnRenamed("totalweight","neg_sentiment")

///////////////////////// Approach 2 Round 2

    //create a DataFrame from the edge DataFrame and add columns corresponding to neg_sentiment after Round 1, inDegree of dst and outDegree of src
    val e1_1_2 = e.join(v7_1_1, e("src") === v7_1_1("id"))
    val e2_1_2 = e1_1_2.join(indeg, e1_1_2("dst") === indeg("id")).select(e1_1_2("*"),indeg("inDegree"))
    val e3_1_2 = e2_1_2.join(outdeg, e2_1_2("src") === outdeg("id")).select(e2_1_2("*"),outdeg("outDegree"))

    //append the Page Rank algorithm weight to each edge and then add this column to the edge DataFrame already containing neg_sentiment, inDegree of dst and outDegree of src.
    val e4_1_2 = e3_1_2.join(pr.edges,(e3_1_2("src") === pr.edges("src")) && (e3_1_2("dst") === pr.edges("dst"))).select(e3_1_2("*"),pr.edges("weight"))

    //then using the DataFrame above, combine the values using the formula (for the second approach) to give a weight to each edge.
    val e5_1_2 = e4_1_2.map(x => (x.getString(0),x.getString(1),1.0 * x.getDouble(3) * x.getDouble(6))).toDF("src", "dst", "weight")
    val g1_1_2 = GraphFrame(v7_1_1, e5_1_2)

    //add the directed weights of the edges to the neg_sentiment value of the destination vertex. In Theory this is the dissemination of negative sentiment throughout the network.
    val agg_1_2 = { g1_1_2.aggregateMessages
      .sendToDst(msgToDst)
      .agg(functions.sum(AM.msg).as("summedweight")) }
    val v4_1_2 = v7_1_1.join(agg_1_2, v7_1_1("id") === agg_1_2("id")).select(v7_1_1("id"), v7_1_1("neg_sentiment"), agg_1_2("summedweight"))
    val v5_1_2 = v4_1_2.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    //rec_1_2 is a list of all vertices with neg_sentiment of 0 before this round of dissemination.
    val rec_1_2 = v4_1_2.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6_1_2 = v7_1_1.filter(!v7_1_1("id").isin(rec_1_2: _*)).withColumn("totalweight", v7_1_1.col("neg_sentiment")).select("id","totalweight")
    val v7_1_2 = v6_1_2.union(v5_1_2).withColumnRenamed("totalweight","neg_sentiment")
    v7_1_2.describe("neg_sentiment")
    v7_1_2.write.csv("../../Data/sent2.csv")
  }
}
