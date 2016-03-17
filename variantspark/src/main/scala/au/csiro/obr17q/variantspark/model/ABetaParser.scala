package au.csiro.obr17q.variantspark.model

import au.com.bytecode.opencsv.CSVParser
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext

/**
  * Created by obr17q on 5/01/2016.
  */

private case class ABetaRecord(individual: String, aBeta: String, features: Vector)

class ABetaParser (val CsvFileNames: String, val sc: SparkContext, val sqlContext: SQLContext) extends scala.Serializable {

  val NotAVariant = "" :: "aBeta" :: Nil

  private val CsvFilesRDD = sc.textFile(CsvFileNames, 5)


  /**
    * Returns the heading row from the FASTA file
    * The heading from each column is stored as a String in a List.
    * List[String]
    */
  val headings: List[String] = {
    CsvFilesRDD
      .filter(_.startsWith(""""","""))
      .map(
        line => {
          val parser = new CSVParser
          parser.parseLine(line)
        } ).map(e => List(e:_*)).first.drop(2)
  }



  /**
    * RDD of an line Arrays
    */
  private def CsvLineRDD: RDD[Array[String]] = {
    CsvFilesRDD
      .filter(!_.startsWith("\"\","))
      .mapPartitions(lines => {
        val parser = new CSVParser
        lines.map(line => {
          parser.parseLine(line)
        })
      })
  }.cache

  /**
    * RDD of Variant IDs zipped with a unique index.
    * RDD[(Variant, VariantIndex)]
    * RDD[(PGM1, 1221)]
    * count = noOfGenes
    */
  def featureTuples: List[(String, Int)] = {
    //val NotAVariant = this.NotAVariant
    headings
      .filter(v => !(NotAVariant contains v))
      .zipWithIndex
  }

  /**
    * RDD[(IndividualID, (BMI, BMI_CAT, MSI_STATUS))]
    * RDD[(TCGA-CA-6717, (30.25, "obese", 2))]
    * count = noOfIndividuals
    */
  private def IndividualMetaData: RDD[(String, (Double, String, String))] = {
    CsvLineRDD
      .map(line => (line(10), (line(27).toDouble, line(28), line(23))))
      .distinct
  }

  val data = sqlContext
    .createDataFrame {
      val featureCount = this.featureCount
      val featureTuples = this.featureTuples
      CsvLineRDD
        .map(p => (p(0), p(1), p.drop(2).zip(featureTuples).map(q => (q._2._2, q._1.toDouble))))
        .map(p =>
          ABetaRecord(
            individual = p._1,
            aBeta = p._2,
            features = Vectors.sparse(featureCount, p._3)
          )
        )
  }.toDF

  /**
    * Number of variants in the file
    */
  private def featureCount : Int = {
    headings.length
  }

  def feature(n: String) : Int = {
    featureTuples.filter(_._1 == n).head._2
  }
}
