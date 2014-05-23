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

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import java.io.{ ByteArrayInputStream, File }

trait FileLocator extends Serializable {

  def relativeLocator(relativePath: String): FileLocator
  def bytes: ByteAccess
}

class S3FileLocator(val credentials: AWSCredentials, val bucket: String, val key: String) extends FileLocator {

  override def relativeLocator(relativePath: String): FileLocator =
    new S3FileLocator(credentials, bucket, "%s/%s".format(key.stripSuffix("/"), relativePath))

  override def bytes: ByteAccess = new S3ByteAccess(new AmazonS3Client(credentials), bucket, key)
}

class LocalFileLocator(val file: File) extends FileLocator {
  override def relativeLocator(relativePath: String): FileLocator = new LocalFileLocator(new File(file, relativePath))
  override def bytes: ByteAccess = new InputStreamByteAccess(file)
}

class ByteArrayLocator(val byteData: Array[Byte]) extends FileLocator {
  override def relativeLocator(relativePath: String): FileLocator = this
  override def bytes: ByteAccess = new ByteArrayByteAccess(byteData)
}