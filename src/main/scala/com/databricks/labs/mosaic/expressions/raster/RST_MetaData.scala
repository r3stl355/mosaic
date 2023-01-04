package com.databricks.labs.mosaic.expressions.raster

import com.databricks.labs.mosaic.core.raster.MosaicRaster
import com.databricks.labs.mosaic.core.raster.api.RasterAPI
import com.databricks.labs.mosaic.expressions.base.{RasterExpression, WithExpressionInfo}
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry.FunctionBuilder
import org.apache.spark.sql.catalyst.expressions.{Expression, NullIntolerant}
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.types._

case class RST_MetaData(inputRaster: Expression, path: Expression, rasterAPI: String)
    extends RasterExpression[RST_MetaData](inputRaster, path, MapType(StringType, StringType), RasterAPI(rasterAPI))
      with NullIntolerant
      with CodegenFallback {

    override def rasterTransform(raster: MosaicRaster): Any = {
        val metaData = raster.metadata
        buildMap(metaData)
    }
}

object RST_MetaData extends WithExpressionInfo {

    override def name: String = "rst_metadata"

    override def usage: String = "_FUNC_(expr1) - Extracts metadata from a raster dataset."

    override def example: String =
        """
          |    Examples:
          |      > SELECT _FUNC_(a);
          |        {"NC_GLOBAL#acknowledgement":"NOAA Coral Reef Watch Program","NC_GLOBAL#cdm_data_type":"Grid"}
          |  """.stripMargin

    override def builder(args: Any*): FunctionBuilder = {
        val rasterAPI = args.headOption.getOrElse("GDAL").toString
        (exprs: Seq[Expression]) =>
            exprs match {
                case e if e.length == 1 => RST_MetaData(exprs(0), lit("").expr, rasterAPI)
                case e if e.length == 2 => RST_MetaData(exprs(0), exprs(1), rasterAPI)
            }
    }

}
