package com.elastiknn.annb

import io.circe.{Json, JsonObject}
import org.bytedeco.hdf5._
import org.bytedeco.javacpp.{FloatPointer, IntPointer}

import java.io.File
import java.nio.FloatBuffer
import java.nio.file.Path
import scala.util.Try

object HDF5Util {

  def readFloats2d(path: Path, mode: Int, name: String): Try[Iterator[Array[Float]]] = Try {
    val file = new H5File(path.toFile.getAbsolutePath, mode)
    val dataSet = file.openDataSet(name)
    val dataSpace = dataSet.getSpace
    val (rows, cols) = {
      val buf = Array(0L, 0L)
      dataSpace.getSimpleExtentDims(buf)
      (buf(0).toInt, buf(1).toInt)
    }
    try {
      val buf = FloatBuffer.allocate(rows * cols)
      val ptr = new FloatPointer(buf)
      val dataType = new DataType(PredType.NATIVE_FLOAT())
      dataSet.read(ptr, dataType)
      ptr.get(buf.array())
      buf.array().grouped(cols)
    } finally {
      dataSpace.close()
      dataSet.close()
      file.close()
    }
  }

  def writeFloats2d(path: Path, mode: Int, name: String, floats: Array[Array[Float]]): Try[Unit] = Try {
    path.getParent.toFile.mkdirs()
    val file = new H5File(path.toFile.getAbsolutePath, mode)
    val (rows, cols) = (floats.length, floats.head.length)
    val dataSpace = new DataSpace(2, Array[Long](rows, cols))
    val dataType = new DataType(PredType.NATIVE_FLOAT())
    val dataSet = new DataSet(file.createDataSet(name, dataType, dataSpace))
    val buffer: Array[Float] = floats.flatten
    try {
      dataSet.write(new FloatPointer(buffer: _*), dataType)
    } finally {
      dataSpace.close()
      dataSet.close()
      file.close()
    }
  }

  def writeFloats1d(path: Path, mode: Int, name: String, floats: Array[Float]): Try[Unit] = Try {
    path.getParent.toFile.mkdirs()
    val file = new H5File(path.toFile.getAbsolutePath, mode)
    val dataSpace = new DataSpace(1, Array[Long](floats.length))
    val dataType = new DataType(PredType.NATIVE_FLOAT())
    val dataSet = new DataSet(file.createDataSet(name, dataType, dataSpace))
    try {
      dataSet.write(new FloatPointer(floats: _*), dataType)
    } finally {
      dataSpace.close()
      dataSet.close()
      file.close()
    }
  }

  def writeInts2d(path: Path, mode: Int, name: String, ints: Array[Array[Int]]): Try[Unit] = Try {
    path.getParent.toFile.mkdirs()
    val file = new H5File(path.toFile.getAbsolutePath, mode)
    val (rows, cols) = (ints.length, ints.head.length)
    val dataSpace = new DataSpace(2, Array[Long](rows, cols))
    val dataType = new DataType(PredType.NATIVE_INT())
    val dataSet = new DataSet(file.createDataSet(name, dataType, dataSpace))
    val buffer: Array[Int] = ints.flatten
    try {
      dataSet.write(new IntPointer(buffer: _*), dataType)
    } finally {
      dataSpace.close()
      dataSet.close()
      file.close()
    }
  }

  def createFileWithAttributes(path: Path, attrs: JsonObject): Try[Unit] = Try {
    val optAppPyscript = new File("/opt/app/hdf5_set_attrs.py")
    val pyscript = if (optAppPyscript.exists()) optAppPyscript.getAbsolutePath else this.getClass.getResource("/hdf5_set_attrs.py").getFile
    import sys.process._
    val cmd: String = s"python3 $pyscript ${path.toFile.getAbsolutePath} ${Json.fromJsonObject(attrs).noSpaces}"
    val res: Int = cmd.!
    assert(res == 0, s"Command [$cmd] failed with exit code [$res]")
  }
}
