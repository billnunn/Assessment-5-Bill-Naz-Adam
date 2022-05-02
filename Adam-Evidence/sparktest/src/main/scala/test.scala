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


object test {

  /*def score(row: Row): Int = {
    val vs = row.where(v3.col("src") === row.getString(0))
    val vi = indeg.where(indeg.col("id") === row.getString(1))
    val vo = outdeg.where(outdeg.col("id") === row.getString(0))
    val s = vs.first().getInt(1) / (vi.first().getInt(1) * (vo.first().getInt(1)))
    s
  }*/
  def calcoutdeg(g: GraphFrame) : DataFrame ={
    g.edges.groupBy(g.edges("src").as(ID)).agg(functions.sum("n").cast("int").as("outDegree"))
  }
  def calcindeg(g: GraphFrame): DataFrame= {
    g.edges.groupBy(g.edges(DST).as(ID)).agg(functions.sum("n").cast("int").as("inDegree"))
  }


  def main(args: Array[String]): Unit = {

    import org.apache.spark.{SparkConf, SparkContext, sql}
    import scala.math._
    // tried to manipulate graphx but easier to expand edges
    Logger.getLogger("org").setLevel(Level.ERROR)
    val spark = SparkSession.builder.master("local").appName("Sparktest").getOrCreate()
    spark.sparkContext.setCheckpointDir("Checkpoint")
    import spark.implicits._
    val edges = spark.read.option("header","true").csv("../../data/edges1.csv").toDF().withColumnRenamed("to","src").withColumnRenamed("from","dst")
    val e = edges.toDF(Seq("src","dst"): _*)
    val v=e.select("src").union(e.select("dst")).distinct().withColumnRenamed("src", "id")
    val fired = spark.read.option("delimiter", ",").csv("../../data/fired1.csv")
    val fired1 = fired.select("_c0").map(x => x.getString(0)).collectAsList().toSeq

    val v1 = v.filter(v("id").isin(fired1: _*)).withColumn("neg_sentiment", lit(1))
    val v2 = v.filter(!v("id").isin(fired1: _*)).withColumn("neg_sentiment", lit(0))
    val v3 = v1.union(v2)

    val g=GraphFrame(v3,e)
    val t1_0 = System.nanoTime
    val cc = g.connectedComponents.run()
    val duration_0 = (System.nanoTime - t1_0) / 1e9d
    println(duration_0)
    cc.describe("component").show()
    //cc.write.csv("../../data/connected.csv")

    val indeg = g.inDegrees
    val outdeg = g.outDegrees

    val t1_1 = System.nanoTime
    val e1 = e.join(v3, e("src") === v3("id"))
    val e2 = e1.join(indeg, e1("dst") === indeg("id")).select(e1("*"),indeg("inDegree"))
    val e3 = e2.join(outdeg, e2("src") === outdeg("id")).select(e2("*"),outdeg("outDegree"))
    val e4 = e3.map(x => (x.getString(0), x.getString(1), 1.0 * x.getInt(3) / (x.getInt(4)))).toDF("src", "dst", "weight")
    val g1 = GraphFrame(v3, e4)

    val AM = AggregateMessages

    val msgToDst = AM.edge("weight")
    val agg = { g1.aggregateMessages
      .sendToDst(msgToDst)  // send source user's age to destination
      .agg(functions.sum(AM.msg).as("summedweight")) } // sum up ages, stored in AM.msg column
    val v4 = v3.join(agg, v3("id") === agg("id")).select(v3("id"), v3("neg_sentiment"), agg("summedweight"))
    val v5 = v4.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    val rec = v4.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6 = v3.filter(!v3("id").isin(rec: _*)).withColumn("totalweight", v3.col("neg_sentiment")).select("id","totalweight")
    val v7 = v6.union(v5).withColumnRenamed("totalweight","neg_sentiment")

////////////////////////////////////////////////////1.2

    val e1_1 = e.join(v7, e("src") === v7("id"))
    val e2_1 = e1_1.join(indeg, e1_1("dst") === indeg("id")).select(e1_1("*"),indeg("inDegree"))
    val e3_1 = e2_1.join(outdeg, e2_1("src") === outdeg("id")).select(e2_1("*"),outdeg("outDegree"))
    val e4_1 = e3_1.map(x => (x.getString(0), x.getString(1), 1.0 * x.getDouble(3) / (x.getInt(4)))).toDF("src", "dst", "weight")
    val g1_1 = GraphFrame(v7, e4_1)


    val agg_1 = { g1_1.aggregateMessages
      .sendToDst(msgToDst)  // send source user's age to destination
      .agg(functions.sum(AM.msg).as("summedweight")) } // sum up ages, stored in AM.msg column
    val v4_1 = v7.join(agg_1, v7("id") === agg_1("id")).select(v7("id"), v7("neg_sentiment"), agg_1("summedweight"))
    val v5_1 = v4_1.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    val rec_1 = v4_1.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6_1 = v7.filter(!v7("id").isin(rec_1: _*)).withColumn("totalweight", v7.col("neg_sentiment")).select("id","totalweight")
    val v7_1 = v6_1.union(v5_1).withColumnRenamed("totalweight","neg_sentiment")
    v7_1.show()
    val duration_1 = (System.nanoTime - t1_1) / 1e9d
    println(duration_1)
    //v7_1.describe("neg_sentiment")
    //v7_1.write.csv("../../data/sent1.csv")

//////////////////////////// 2.1
    val t1_2 = System.nanoTime
    val pr = g.pageRank.maxIter(5).run()
    val e4_1_1 = e3.join(pr.edges,(e3("src") === pr.edges("src")) && (e3("dst") === pr.edges("dst"))).select(e3("*"),pr.edges("weight"))
    val e5_1_1 = e4_1_1.map(x => (x.getString(0),x.getString(1),1.0 * x.getInt(3) * x.getDouble(6))).toDF("src", "dst", "weight")
    val g_1_1 = GraphFrame(v3,e5_1_1)

    val agg_1_1 = { g_1_1.aggregateMessages
      .sendToDst(msgToDst)  // send source user's age to destination
      .agg(functions.sum(AM.msg).as("summedweight")) }
    val v4_1_1 = v3.join(agg_1_1, v3("id") === agg_1_1("id")).select(v3("id"), v3("neg_sentiment"), agg_1_1("summedweight"))
    val v5_1_1 = v4_1_1.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    val rec_1_1 = v4_1_1.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6_1_1 = v3.filter(!v3("id").isin(rec_1_1: _*)).withColumn("totalweight", v3.col("neg_sentiment")).select("id","totalweight")
    val v7_1_1 = v6_1_1.union(v5_1_1).withColumnRenamed("totalweight","neg_sentiment")

///////////////////////// 2.2

    val e1_1_2 = e.join(v7_1_1, e("src") === v7_1_1("id"))
    val e2_1_2 = e1_1_2.join(indeg, e1_1_2("dst") === indeg("id")).select(e1_1_2("*"),indeg("inDegree"))
    val e3_1_2 = e2_1_2.join(outdeg, e2_1_2("src") === outdeg("id")).select(e2_1_2("*"),outdeg("outDegree"))
    val e4_1_2 = e3_1_2.join(pr.edges,(e3_1_2("src") === pr.edges("src")) && (e3_1_2("dst") === pr.edges("dst"))).select(e3_1_2("*"),pr.edges("weight"))
    val e5_1_2 = e4_1_2.map(x => (x.getString(0),x.getString(1),1.0 * x.getDouble(3) * x.getDouble(6))).toDF("src", "dst", "weight")

    val g1_1_2 = GraphFrame(v7_1_1, e5_1_2)


    val agg_1_2 = { g1_1_2.aggregateMessages
      .sendToDst(msgToDst)  // send source user's age to destination
      .agg(functions.sum(AM.msg).as("summedweight")) } // sum up ages, stored in AM.msg column
    val v4_1_2 = v7_1_1.join(agg_1_2, v7_1_1("id") === agg_1_2("id")).select(v7_1_1("id"), v7_1_1("neg_sentiment"), agg_1_2("summedweight"))
    val v5_1_2 = v4_1_2.withColumn("totalweight", col("summedweight") + col("neg_sentiment")).select("id","totalweight")
    val rec_1_2 = v4_1_2.select("id").map(x => x.getString(0)).collectAsList().toSeq
    val v6_1_2 = v7_1_1.filter(!v7_1_1("id").isin(rec_1_2: _*)).withColumn("totalweight", v7_1_1.col("neg_sentiment")).select("id","totalweight")
    val v7_1_2 = v6_1_2.union(v5_1_2).withColumnRenamed("totalweight","neg_sentiment")
    v7_1_2.show()
    val duration_2 = (System.nanoTime - t1_2) / 1e9d
    println(duration_2)
    //v7_1_2.describe("neg_sentiment")
    //v7_1_2.write.csv("../../data/sent2.csv")

  }


}
