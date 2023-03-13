package com.databricks.labs.mosaic.datasource

import com.databricks.labs.mosaic.expressions.util.OGRReadeWithOffset
import org.apache.spark.sql.QueryTest
import org.apache.spark.sql.test.SharedSparkSession
import org.apache.spark.sql.types._
import org.gdal.ogr.ogr
import org.scalatest.matchers.must.Matchers.{be, noException, not}
import org.scalatest.matchers.should.Matchers.{an, convertToAnyShouldWrapper}

class GDALFileFormatTest extends QueryTest with SharedSparkSession {

    test("Read netcdf with GDALFileFormat") {
        assume(System.getProperty("os.name") == "Linux")

        val netcdf = "/binary/netcdf-coral/"
        val filePath = getClass.getResource(netcdf).getPath

        noException should be thrownBy spark.read
            .format("gdal")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "NetCDF")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "NetCDF")
            .load(filePath)
            .select("proj4Str")
            .take(1)

    }

    test("Read grib with GDALFileFormat") {
        assume(System.getProperty("os.name") == "Linux")

        val grib = "/binary/grib-cams/"
        val filePath = getClass.getResource(grib).getPath

        noException should be thrownBy spark.read
            .format("gdal")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "NetCDF")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "NetCDF")
            .load(filePath)
            .select("proj4Str")
            .take(1)

    }

    test("Read tif with GDALFileFormat") {
        assume(System.getProperty("os.name") == "Linux")

        val tif = "/modis/"
        val filePath = getClass.getResource(tif).getPath

        noException should be thrownBy spark.read
            .format("gdal")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "TIF")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "TIF")
            .load(filePath)
            .select("proj4Str")
            .take(1)

    }

    test("Read zarr with GDALFileFormat") {
        assume(System.getProperty("os.name") == "Linux")

        val zarr = "/binary/zarr-example/"
        val filePath = getClass.getResource(zarr).getPath

        noException should be thrownBy spark.read
            .format("gdal")
            .option("vsizip", "true")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "Zarr")
            .option("vsizip", "true")
            .load(filePath)
            .take(1)

        noException should be thrownBy spark.read
            .format("gdal")
            .option("driverName", "Zarr")
            .option("vsizip", "true")
            .load(filePath)
            .select("proj4Str")
            .take(1)

    }

    test("GDALFileFormat utility tests") {
        val reader = new GDALFileFormat()
        an[Error] should be thrownBy reader.prepareWrite(spark, null, null, null)

        for (
          driver <- Seq(
            "GTiff",
            "HDF4",
            "HDF5",
            "JP2ECW",
            "JP2KAK",
            "JP2MrSID",
            "JP2OpenJPEG",
            "NetCDF",
            "PDF",
            "PNG",
            "VRT",
            "XMP",
            "COG",
            "GRIB",
            "Zarr"
          )
        ) {GDALFileFormat.getFileExtension(driver) should not be ""}

        GDALFileFormat.getFileExtension("NotADriver") should be("UNSUPPORTED")

        noException should be thrownBy OGRFileFormat.enableOGRDrivers(force = true)

        val path = getClass.getResource("/binary/geodb/").getPath.replace("file:", "")
        val ds = ogr.Open(s"/vsizip/$path", 0)

        noException should be thrownBy OGRFileFormat.getLayer(ds, 0, "layer2")

        OGRFileFormat.getType("Boolean").typeName should be("boolean")
        OGRFileFormat.getType("Integer").typeName should be("integer")
        OGRFileFormat.getType("String").typeName should be("string")
        OGRFileFormat.getType("Real").typeName should be("double")
        OGRFileFormat.getType("Date").typeName should be("date")
        OGRFileFormat.getType("Time").typeName should be("timestamp")
        OGRFileFormat.getType("DateTime").typeName should be("timestamp")
        OGRFileFormat.getType("Binary").typeName should be("binary")
        OGRFileFormat.getType("IntegerList").typeName should be("array")
        OGRFileFormat.getType("RealList").typeName should be("array")
        OGRFileFormat.getType("StringList").typeName should be("array")
        OGRFileFormat.getType("WideString").typeName should be("string")
        OGRFileFormat.getType("WideStringList").typeName should be("array")
        OGRFileFormat.getType("Integer64").typeName should be("long")
        OGRFileFormat.getType("Integer64List").typeName should be("string")

        OGRFileFormat.coerceTypeList(Seq(DoubleType, LongType)).typeName should be("long")
        OGRFileFormat.coerceTypeList(Seq(IntegerType, LongType)).typeName should be("long")
        OGRFileFormat.coerceTypeList(Seq(IntegerType, DoubleType)).typeName should be("double")
        OGRFileFormat.coerceTypeList(Seq(IntegerType, StringType)).typeName should be("integer")
        OGRFileFormat.coerceTypeList(Seq(StringType, ShortType)).typeName should be("short")
        OGRFileFormat.coerceTypeList(Seq(StringType, ByteType)).typeName should be("binary")
        OGRFileFormat.coerceTypeList(Seq(StringType, BinaryType)).typeName should be("binary")
        OGRFileFormat.coerceTypeList(Seq(StringType, StringType)).typeName should be("string")
        OGRFileFormat.coerceTypeList(Seq(StringType, DateType)).typeName should be("date")
        OGRFileFormat.coerceTypeList(Seq(StringType, BooleanType)).typeName should be("boolean")
        OGRFileFormat.coerceTypeList(Seq(StringType, TimestampType)).typeName should be("timestamp")

        val feature = ds.GetLayer(0).GetNextFeature()
        noException should be thrownBy OGRFileFormat.getFieldIndex(feature, "SHAPE")
        noException should be thrownBy OGRFileFormat.getFieldIndex(feature, "field1")
        an[Error] should be thrownBy OGRFileFormat.getDate(feature, 1)
        OGRReadeWithOffset(null, null, null, null).position should be(false)
    }

}
