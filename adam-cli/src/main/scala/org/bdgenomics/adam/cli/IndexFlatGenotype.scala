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
package org.bdgenomics.adam.cli

import org.kohsuke.args4j.{ Option, Argument }
import org.bdgenomics.adam.util.ParquetLogger
import java.io.File
import org.bdgenomics.adam.avro.ADAMFlatGenotype
import org.bdgenomics.adam.parquet_reimpl.index.{ RangeIndexWriter, RangeIndexGenerator }
import java.util.logging.Level

import org.bdgenomics.adam.rich.ReferenceMappingContext._

object IndexFlatGenotype extends ADAMCommandCompanion {
  val commandName: String = "indexfgenotype"
  val commandDescription: String = "Indexes Parquet files containing ADAMFlatGenotype records"

  def apply(cmdLine: Array[String]): ADAMCommand = {
    new IndexFlatGenotype(Args4j[IndexFlatGenotypeArgs](cmdLine))
  }
}

class IndexFlatGenotypeArgs extends Args4jBase with ParquetArgs {
  @Argument(required = true, metaVar = "INDEX", usage = "The index file to generate", index = 0)
  var indexFile: String = null
  @Argument(required = true, metaVar = "PARQUET_LIST", usage = "Comma-separated list of parquet file paths", index = 1)
  var listOfParquetFiles: String = null
}

class IndexFlatGenotype(args: IndexFlatGenotypeArgs) extends ADAMCommand {
  val companion = IndexFlatGenotype

  def run() = {
    // Quiet parquet...
    ParquetLogger.hadoopLoggerLevel(Level.SEVERE)

    val indexWriter: RangeIndexWriter = new RangeIndexWriter(new File(args.indexFile))
    val generator: RangeIndexGenerator[ADAMFlatGenotype] = new RangeIndexGenerator[ADAMFlatGenotype]()

    args.listOfParquetFiles.split(",").foreach {
      case parquetFilePath: String =>
        generator.addParquetFile(parquetFilePath).foreach(indexWriter.write)
    }

    indexWriter.close()
  }
}
