/**
 * Copyright 2014 Genome Bridge LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.adam.parquet_reimpl

import parquet.filter.UnboundRecordFilter
import parquet.io.api.RecordMaterializer
import parquet.io.ColumnIOFactory
import parquet.column.page.{ PageReadStore, PageReader }
import parquet.column.ColumnDescriptor
import org.apache.hadoop.io.compress.{ CompressionCodec => HadoopCompressionCodec, CodecPool }
import org.apache.spark.{ SparkContext, Partition }
import org.bdgenomics.adam.parquet_reimpl.ParquetSchemaType
import org.bdgenomics.adam.rdd._
import org.bdgenomics.adam.rdd.ParquetColumnDescriptor
import org.bdgenomics.adam.rdd.ParquetRowGroup
import org.apache.hadoop.conf.Configuration

class ParquetPartition(val index: Int,
                       val rowGroup: ParquetRowGroup,
                       val requestedSchema: ParquetSchemaType,
                       val actualSchema: ParquetSchemaType)
    extends Partition {

  class PartitionPageReadStore(chunkMap: Map[ParquetColumnDescriptor, PageReader])
      extends PageReadStore {

    override def getPageReader(cd: ColumnDescriptor): PageReader =
      chunkMap
        .get(new ParquetColumnDescriptor(cd))
        .getOrElse(
          throw new NoSuchElementException("Could not find %s in the map %s".format(cd.getPath.mkString("."), chunkMap.keys.map(_.path.mkString(".")).mkString(","))))
    override def getRowCount: Long = rowGroup.rowCount
  }

  def materializeRecords[T](config: Configuration,
                            io: ByteAccess,
                            recordMaterializer: RecordMaterializer[T],
                            filter: UnboundRecordFilter): Iterator[T] = {

    assert(filter != null, "filter was null")

    val requestedPaths = requestedSchema.paths()

    val requestedColumnChunks: Seq[ParquetColumnChunk] = rowGroup.columnChunks.filter {
      cc => requestedPaths.contains(TypePath(cc.columnDescriptor.path))
    }

    assert(config != null, "HadoopConfiguration was null")

    val decompressor: Option[HadoopCompressionCodec] =
      CompressionCodecEnum.getHadoopCodec(rowGroup.columnChunks.head.compressionCodec, config)

    val chunkMap = requestedColumnChunks
      .map(cc => (cc.columnDescriptor, cc.readAllPages(decompressor, io)))
      .toMap
    val pageReadStore = new PartitionPageReadStore(chunkMap)

    val columnIOFactory: ColumnIOFactory = new ColumnIOFactory
    val columnIO = columnIOFactory.getColumnIO(requestedSchema.convertToParquet(), actualSchema.convertToParquet())
    val reader = columnIO.getRecordReader[T](pageReadStore, recordMaterializer, filter)

    assert(reader.getClass.getName == "parquet.io.FilteredRecordReader", "class name %s wasn't FilteredRecordReader".format(reader.getClass.getName))

    new Iterator[T] {
      var nextT: T = reader.read()

      override def next(): T = {
        val ret = nextT
        nextT = reader.read()
        ret
      }

      override def hasNext: Boolean = nextT != null
    }
  }
}
