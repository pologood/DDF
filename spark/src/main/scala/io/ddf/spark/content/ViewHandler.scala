/**
  *
  */
package io.ddf.spark.content

import io.ddf.DDF
import io.ddf.content.IHandleViews

import scala.collection.JavaConverters._
import io.ddf.content.Schema
import io.ddf.spark.{SparkDDF, SparkDDFManager}
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.rdd.RDD

import scala.collection.JavaConversions._
import scala.util.Random
import org.apache.spark.mllib.random.RandomRDDs._
import org.apache.spark.sql.types.{LongType, StructField, StructType}

import scala.collection.JavaConverters

/**
  * RDD-based ViewHandler
  *
  *
  */
class ViewHandler(mDDF: DDF) extends io.ddf.content.ViewHandler(mDDF) with IHandleViews {

  object ViewFormat extends Enumeration {
    type ViewFormat = Value
    val DEFAULT, ARRAY_OBJECT, ARRAY_DOUBLE, TABLE_PARTITION, LABELED_POINT, LABELED_POINTS = Value
  }

  import ViewFormat._

  /**
    * Same as {@link #get(int[], int)}, but accepts a scala.Enumeration for format instead.
    *
    * @param columns
    * @param format
    * A scala.Enumeration that will be converted to an integer by calling
    * formatEnum.toString()
    * @return
    */
  def get(columns: Array[Int], format: ViewFormat): DDF = {
    format match {
      case ViewFormat.DEFAULT => ViewHandler.getDefault(columns, mDDF)
      case ViewFormat.ARRAY_OBJECT => ViewHandler.getArrayObject(columns, mDDF)
      case ViewFormat.ARRAY_DOUBLE => ViewHandler.getArrayDouble(columns, mDDF)
      case ViewFormat.TABLE_PARTITION => ViewHandler.getTablePartition(columns, mDDF)
      case ViewFormat.LABELED_POINT => ViewHandler.getLabeledPoint(columns, mDDF)
      case ViewFormat.LABELED_POINTS => ViewHandler.getLabeledPoints(columns, mDDF)
      case _ => {}
    }
    null
  }

  protected def getImpl(columns: Array[Int], format: String): DDF = {
    this.get(columns, ViewFormat.withName(format))
  }

  override def getRandomSampleByNum(numSamples: Long, withReplacement: Boolean,
                                    seed:
                                    Int): DDF = {

    if (numSamples < 0) {
      throw new IllegalArgumentException("Number of samples must be larger than or equal to 0")
    }

    val numRows = mDDF.getNumRows
    var fraction = 1.0
    // We use Spark's API to sample twice what we need, then only pick the first numSamples rows.
    if (withReplacement) fraction = 2.0 * numSamples / numRows

    var sampleDDF: DDF = null
    val manager = this.getManager
    val schema: Schema = new Schema(null,
      JavaConverters.asScalaBufferConverter(mDDF.getSchema.getColumns).asScala.toArray)
    // Optimized running time for integer values by using Spark DataFrame sample and limit APIs
    val numSamplesInt = numSamples.toInt
    if (numSamplesInt == numSamples) {
      val sparkDF = mDDF.getRepresentationHandler.get(classOf[DataFrame]).asInstanceOf[DataFrame]
      val sampleDF = sparkDF.sample(withReplacement, fraction, seed).limit(numSamples.toInt)

      sampleDDF = manager.newDDF(this.getManager, sampleDF, Array(classOf[DataFrame]), null, null, schema)

    } else {
      val rddRow: RDD[Row] = mDDF.asInstanceOf[SparkDDF].getRDD(classOf[Row])
      val sampledRDD = rddRow.sample(withReplacement, fraction, seed).zipWithIndex().filter(_._2 < numSamples).map(_._1)

      sampleDDF = manager.newDDF(sampledRDD, Array(classOf[RDD[_]], classOf[Row]), manager.getNamespace, null, schema)
    }

    // Copy Factor info
    sampleDDF.getMetaDataHandler.copyFactor(mDDF)

    sampleDDF
  }

  override def getRandomSample(numSamples: Int, withReplacement: Boolean, seed: Int): java.util.List[Array[Object]] = {
    if (numSamples < 0) {
      throw new IllegalArgumentException("Number of samples must be larger than or equal to 0");
    } else {
      if (mDDF.getRepresentationHandler.has(classOf[RDD[_]], classOf[Array[Object]])) {
        val rdd = mDDF.asInstanceOf[SparkDDF].getRDD(classOf[Array[Object]])
        val sampleData = rdd.takeSample(withReplacement, numSamples, seed).toList.asJava
        sampleData
      } else {
        val rdd = mDDF.asInstanceOf[SparkDDF].getRDD(classOf[Row])
        rdd.takeSample(withReplacement, numSamples, seed).map {
          row => {
            row.toSeq.toArray.asInstanceOf[Array[Object]]
          }
        }.toList.asJava
      }
    }
  }

  override def getRandomSample(fraction: Double, withReplacement: Boolean, seed: Int): DDF = {
    if (!withReplacement && (fraction > 1 || fraction < 0)) {
      throw new IllegalArgumentException("Sampling fraction must be from 0 to 1 in sampling without replacement")
    }

    if (withReplacement && (fraction < 0)) {
      throw new IllegalArgumentException("Sampling fraction must be larger or equal to 0 in sampling with replacement")
    }

    getRandomSampleByNum(Math.round(fraction * mDDF.getNumRows), withReplacement, seed)

  }

  override def getRandomSampleApprox(fraction: Double, withReplacement: Boolean, seed: Int): DDF = {
    if (!withReplacement && (fraction > 1 || fraction < 0)) {
      throw new IllegalArgumentException("Sampling fraction must be from 0 to 1 in sampling without replacement")
    }

    if (withReplacement && (fraction < 0)) {
      throw new IllegalArgumentException("Sampling fraction must be larger or equal to 0 in sampling with replacement")
    }

    val df: DataFrame = mDDF.getRepresentationHandler.get(classOf[DataFrame]).asInstanceOf[DataFrame]
    val sample_df = df.sample(withReplacement, fraction, seed)
    val schema = SchemaHandler.getSchemaFromDataFrame(sample_df)
    schema.setTableName(mDDF.getSchemaHandler.newTableName())
    val manager = this.getManager
    val sampleDDF = manager.newDDF(manager, sample_df, Array
    (classOf[DataFrame]), manager.getNamespace,
      null, schema)
    mLog.info(">>>>>>> adding ddf to DDFManager " + sampleDDF.getName)
    this.getDDF.getSchemaHandler.getColumns.foreach {
      col => if (col.getOptionalFactor != null) {
        sampleDDF.getSchemaHandler.setAsFactor(col.getName)
      }
    }

    sampleDDF

  }
}

object ViewHandler {
  def getDefault(cols: Array[Int], theDDF: DDF): DDF = {

    null
  }

  def getArrayObject(cols: Array[Int], theDDF: DDF): DDF = {

    null
  }

  def getArrayDouble(cols: Array[Int], theDDF: DDF): DDF = {

    null
  }

  def getTablePartition(cols: Array[Int], theDDF: DDF): DDF = {

    null
  }

  def getLabeledPoint(cols: Array[Int], theDDF: DDF): DDF = {

    null
  }

  def getLabeledPoints(cols: Array[Int], theDDF: DDF): DDF = {

    null
  }
}
