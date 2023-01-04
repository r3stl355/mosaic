package com.databricks.labs.mosaic.functions

import com.databricks.labs.mosaic.expressions.base.WithExpressionInfo
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionDescription, ExpressionInfo}
import org.apache.spark.sql.catalyst.FunctionIdentifier
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry.FunctionBuilder

import scala.collection.immutable
import scala.reflect.runtime.universe
import scala.reflect.ClassTag
import scala.util.Try

case class MosaicRegistry(registry: FunctionRegistry, database: Option[String] = None) {

    def getAnnotations[T: universe.TypeTag]: immutable.Seq[universe.Annotation] = {
        universe.typeOf[T].members.foldLeft(List.empty[universe.Annotation]) { (acc, member) => acc ++ member.annotations }
    }

    def getCompanion[T: universe.TypeTag]: Any = {
        universe.runtimeMirror(getClass.getClassLoader).reflectModule(universe.typeOf[T].typeSymbol.companion.asModule).instance
    }

    def registerExpression[T <: Expression: universe.TypeTag: ClassTag](args: Any*): Unit = registerExpression[T](None, None, args)

    def registerExpression[T <: Expression: universe.TypeTag: ClassTag](alias: String, args: Any*): Unit =
        registerExpression[T](alias = Some(alias), None, args)

    def registerExpression[T <: Expression: universe.TypeTag: ClassTag](builder: FunctionBuilder, args: Any*): Unit =
        registerExpression[T](None, builder = Some(builder), args)

    def registerExpression[T <: Expression: universe.TypeTag: ClassTag](alias: String, builder: FunctionBuilder, args: Any*): Unit =
        registerExpression[T](alias = Some(alias), builder = Some(builder), args)

    private def registerExpression[T <: Expression: universe.TypeTag: ClassTag](
                                                                                   alias: Option[String],
                                                                                   builder: Option[FunctionBuilder],
                                                                                   args: Any*
                                                                               ): Unit = {
        Try {
            val companion = getCompanion[T].asInstanceOf[WithExpressionInfo]
            val annotations = getAnnotations[T]
            val expressionInfoVal = annotations
                .find(_.tree.tpe =:= universe.typeOf[ExpressionDescription])
                .getOrElse(
                    companion.getExpressionInfo[T]()
                )
                .asInstanceOf[ExpressionInfo]
            val builderVal = builder.getOrElse(companion.builder(args))
            val nameVal = alias.getOrElse(companion.name)

            registry.registerFunction(
                FunctionIdentifier(nameVal, database),
                expressionInfoVal,
                builderVal
            )
        }
    }

}
