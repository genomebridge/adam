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
package edu.berkeley.cs.amplab.adam.converters

import edu.berkeley.cs.amplab.adam.avro.ADAMFlatGenotype

import java.io._

import scala.collection.JavaConversions._


class VCFLineParser(inputStream : InputStream) extends Iterator[VCFLine] {

  val reader = new BufferedReader(new InputStreamReader(inputStream))
  var nextLine : VCFLine = null
  var samples : Array[String] = null

  findNextLine()

  private def findNextLine() {
    nextLine = null

    var readLine :String = reader.readLine()
    while(readLine != null && readLine.startsWith("#")) {
      if(readLine.startsWith("#CHROM")) {
        val array = readLine.split("\t")
        samples = array.slice(9, array.length)
      }

      readLine = reader.readLine()
    }

    if(readLine != null) {
      nextLine = new VCFLine(readLine, samples)
    }
  }

  def next(): VCFLine =  {
    val retLine = nextLine
    findNextLine()
    retLine
  }

  def hasNext: Boolean = nextLine != null

  def close() { reader.close() }
}

class VCFLine(vcfLine : String, val samples : Array[String]) {

  private val array = vcfLine.split("\t")

  // CHROM POS ID REF ALT QUAL FILTER INFO FORMAT SAMPLES*

  val referenceName = array(0)
  val position = array(1).toInt
  val id = array(2)
  val ref = array(3)
  val alts : List[CharSequence] = array(4).split(",").toList
  val qual = array(5) match {
    case "." => null
    case x : CharSequence => x.toDouble
  }
  val filter = array(6)

  val info = array(7).split(";").map {
    keyValue => {
      if(keyValue.indexOf("=") != -1) {
        val kvArr = keyValue.split("=")
        kvArr(0) -> kvArr(1)
      } else {
        keyValue -> null
      }
    }
  }.toMap

  val format = array(8).split(":")
  val alleleArray = ref :: alts

  val sampleFields =
    for(i <- 9 until array.length)
      yield format.zip(array(i).split(":")).toMap

}

object VCFLineConverter {

  def convert(line : VCFLine) : Seq[ADAMFlatGenotype] = {

    val baseBuilder = ADAMFlatGenotype.newBuilder()
      .setReferenceName(line.referenceName)
      .setPosition(line.position)
      .setReferenceAllele(line.ref)


    def buildGenotype(i : Int): ADAMFlatGenotype = {
      val sampleFieldMap = line.sampleFields(i)
      val gts = sampleFieldMap("GT").split("\\||/").map(_.toInt)
      val genotypes : Seq[CharSequence] = gts.map(line.alleleArray(_))
      val sampleId = line.samples(i)

      val flatGenotype = ADAMFlatGenotype.newBuilder(baseBuilder)
        .setSampleId(sampleId)
        .setAlleles(genotypes)
        .build()

      flatGenotype
    }

    val genotypes =
      for(i <- 0 until line.samples.length)
        yield buildGenotype(i)


    genotypes
  }


}