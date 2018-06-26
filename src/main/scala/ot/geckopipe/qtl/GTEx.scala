package ot.geckopipe.qtl

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}
import ot.geckopipe.Configuration

object GTEx extends LazyLogging {
  val schema = StructType(
    StructField("variant_id", StringType) ::
      StructField("gene_id", StringType) ::
      StructField("tss_distance", LongType) ::
      StructField("ma_samples", LongType) ::
      StructField("ma_count", LongType) ::
      StructField("maf", DoubleType) ::
      StructField("pval_nominal", DoubleType) ::
      StructField("slope", DoubleType) ::
      StructField("slope_se", DoubleType) ::
      StructField("pval_nominal_threshold", DoubleType) ::
      StructField("min_pval_nominal", DoubleType) ::
      StructField("pval_beta", DoubleType) :: Nil)

  def loadVGPairs(from: String)(implicit ss: SparkSession): DataFrame = {
    import ss.implicits._

    val f2t = udf((filename: String) =>
      extractFilename(filename))

    val removeBuild = udf((variantID: String) =>
      variantID.stripSuffix("_b37"))

    val cleanGeneID = udf((geneID: String) => {
      if (geneID.nonEmpty && geneID.contains("."))
        geneID.split("\\.")(0)
      else
        geneID
    })

    val loaded = ss.read
      .format("csv")
      .option("header", "true")
      .option("inferSchema", "false")
      .option("delimiter","\t")
      .option("mode", "DROPMALFORMED")
      .schema(schema)
      .load(from)
      .withColumn("filename", input_file_name)
      .withColumn("filename",
        when($"filename".isNotNull, f2t($"filename"))
          .otherwise(""))
      .withColumn("gene_id",
        when($"gene_id".contains("."),
          cleanGeneID($"gene_id")))
      .withColumn("variant_id", removeBuild($"variant_id"))

    loaded
  }

  /** load tissue file */
  def buildTissue(from: String)(implicit ss: SparkSession): DataFrame = {
    val tissueCodes = ss.read
      .format("csv")
      .option("header", "true")
      .option("inferSchema", "true")
      .option("delimiter",",")
      .option("mode", "DROPMALFORMED")
      .load(from)

    tissueCodes
  }


  /** build gtex dataset using variant gene map and pivot all tissues */
  def apply(conf: Configuration)(implicit ss: SparkSession): DataFrame = {
    import ss.implicits._

    logger.info(s"build gtex dataframe using map ${conf.gtex.tissueMap}")
    val tissues = GTEx.buildTissue(conf.gtex.tissueMap)

    logger.info("load variant gene pairs with tissue information")
    val vgPairs = GTEx.loadVGPairs(conf.gtex.variantGenePairs)

    logger.info("join variant gene pairs with tissue code from tissue map")
    val r = vgPairs.join(tissues, Seq("filename"), "left_outer")
      .withColumn("feature", lit("tissue"))
      .withColumn("source_id", lit("gtex"))
      .withColumnRenamed("uberon_code", "tissue_id")
      .withColumn("value", array($"pval_nominal", $"slope", $"slope_se", $"pval_beta"))
      .drop("filename", "gtex_tissue", "ma_samples",
        "ma_count", "maf", "pval_nominal_threshold", "min_pval_nominal", "tss_distance",
        "pval_nominal", "slope", "slope_se", "pval_beta")

    r
  }

  private[geckopipe] def extractFilename(from: String): String = from.split('/').last
}