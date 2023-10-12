package com.databricks.labs.mosaic.expressions.raster

import com.databricks.labs.mosaic.core.index.IndexSystemFactory
import com.databricks.labs.mosaic.core.raster.api.RasterAPI
import com.databricks.labs.mosaic.core.raster.gdal_raster.RasterCleaner
import com.databricks.labs.mosaic.core.raster.operator.merge.MergeRasters
import com.databricks.labs.mosaic.core.types.RasterTileType
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.expressions.raster.base.RasterExpressionSerialization
import com.databricks.labs.mosaic.functions.MosaicExpressionConfig
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.aggregate.{ImperativeAggregate, TypedImperativeAggregate}
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionInfo, UnsafeProjection, UnsafeRow}
import org.apache.spark.sql.catalyst.trees.UnaryLike
import org.apache.spark.sql.catalyst.util.GenericArrayData
import org.apache.spark.sql.types.{ArrayType, BinaryType, DataType, LongType}

import scala.collection.mutable.ArrayBuffer

/**
  * Returns a set of new rasters with the specified tile size (tileWidth x
  * tileHeight).
  */
//noinspection DuplicatedCode
case class RST_MergeAgg(
    rasterExpr: Expression,
    expressionConfig: MosaicExpressionConfig,
    mutableAggBufferOffset: Int = 0,
    inputAggBufferOffset: Int = 0
) extends TypedImperativeAggregate[ArrayBuffer[Any]]
      with UnaryLike[Expression]
      with RasterExpressionSerialization {

    override lazy val deterministic: Boolean = true
    override val child: Expression = rasterExpr
    override val nullable: Boolean = false
    override val dataType: DataType = RasterTileType(LongType)
    override def prettyName: String = "rst_merge_agg"

    val rasterAPI: RasterAPI = RasterAPI.apply(expressionConfig.getRasterAPI)

    private lazy val projection = UnsafeProjection.create(Array[DataType](ArrayType(elementType = dataType, containsNull = false)))
    private lazy val row = new UnsafeRow(1)

    def update(buffer: ArrayBuffer[Any], input: InternalRow): ArrayBuffer[Any] = {
        val value = child.eval(input)
        buffer += InternalRow.copyValue(value)
        buffer
    }

    def merge(buffer: ArrayBuffer[Any], input: ArrayBuffer[Any]): ArrayBuffer[Any] = {
        buffer ++= input
    }

    override def createAggregationBuffer(): ArrayBuffer[Any] = ArrayBuffer.empty

    override def withNewInputAggBufferOffset(newInputAggBufferOffset: Int): ImperativeAggregate =
        copy(inputAggBufferOffset = newInputAggBufferOffset)

    override def withNewMutableAggBufferOffset(newMutableAggBufferOffset: Int): ImperativeAggregate =
        copy(mutableAggBufferOffset = newMutableAggBufferOffset)

    override def eval(buffer: ArrayBuffer[Any]): Any = {

        if (buffer.isEmpty) {
            null
        } else if (buffer.size == 1) {
            buffer.head
        } else {

            // Do do move the expression
            val tiles = buffer.map(row => MosaicRasterTile.deserialize(row.asInstanceOf[InternalRow], expressionConfig.getCellIdType, rasterAPI))

            // If merging multiple index rasters, the index value is dropped
            val idx = if (tiles.map(_.index).groupBy(identity).size == 1) tiles.head.index else null
            val merged = MergeRasters.merge(tiles.map(_.raster))
            // TODO: should parent path be an array?
            val parentPath = tiles.head.parentPath
            val driver = tiles.head.driver

            val result = MosaicRasterTile(idx, merged, parentPath, driver)
                .formatCellId(IndexSystemFactory.getIndexSystem(expressionConfig.getIndexSystem))
                .serialize(rasterAPI, BinaryType, expressionConfig.getRasterCheckpoint)

            tiles.foreach(RasterCleaner.dispose)
            RasterCleaner.dispose(merged)

            result
        }
    }

    override def serialize(obj: ArrayBuffer[Any]): Array[Byte] = {
        val array = new GenericArrayData(obj.toArray)
        projection.apply(InternalRow.apply(array)).getBytes
    }

    override def deserialize(bytes: Array[Byte]): ArrayBuffer[Any] = {
        val buffer = createAggregationBuffer()
        row.pointTo(bytes, bytes.length)
        row.getArray(0).foreach(dataType, (_, x: Any) => buffer += x)
        buffer
    }

    override protected def withNewChildInternal(newChild: Expression): RST_MergeAgg = copy(rasterExpr = newChild)

}

/** Expression info required for the expression registration for spark SQL. */
object RST_MergeAgg {

    def registryExpressionInfo(db: Option[String]): ExpressionInfo =
        new ExpressionInfo(
          classOf[RST_MergeAgg].getCanonicalName,
          db.orNull,
          "rst_merge_agg",
          """
            |    _FUNC_(tiles)) - Merges rasters into a single raster.
            """.stripMargin,
          "",
          """
            |    Examples:
            |      > SELECT _FUNC_(rasters);
            |        raster
            |  """.stripMargin,
          "",
          "agg_funcs",
          "1.0",
          "",
          "built-in"
        )

}
