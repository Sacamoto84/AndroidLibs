package s3

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import timber.log.Timber


class S3(endpoint: String, key: String, secret: String) {

    lateinit var minioClient: MinioClient

    init {
        try {
            minioClient = MinioClient.builder().endpoint(endpoint)   //"http://xx.xx.xx.xx:9000"
                .credentials(key, secret).build()


            minioClient.bucketExists(
                BucketExistsArgs.builder().bucket("ping").build()
            )

            //            minioClient.listBuckets().forEach {
            //                println(">" + it.name())
            //                minioClient.listObjects(
            //                    ListObjectsArgs.builder().bucket(it.name()).build()
            //                )?.forEach {file->
            //                    println("-- "+file.get().objectName() + " " +  file.get().etag())
            //                }
            //            }

            println("!!!Есть подключение к S3")

        } catch (e: Exception) {
            Timber.e("!!!Ошибка подключения к S3: ${e.localizedMessage}")
        }
    }


}