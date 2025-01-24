package top.bearcabbage.mirrortree;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;

import java.io.File;
import java.io.IOException;

import static com.mojang.text2speech.Narrator.LOGGER;

public class TencentCloudCosUpload {
    // 1 初始化用户身份信息(secretId, secretKey)
    static COSCredentials cred = new BasicCOSCredentials("xxx", "xxx");
    // 2 设置bucket的区域, COS地域的简称请参照 https://cloud.tencent.com/document/product/436/6224
    // clientConfig中包含了设置region, https(默认http), 超时, 代理等set方法, 使用可参见源码或者接口文档FAQ中说明
    static ClientConfig clientConfig = new ClientConfig(new Region("ap-beijing"));
    // 3 生成cos客户端
    static COSClient cosClient = new COSClient(cred, clientConfig);
    // bucket的命名规则为{name}-{appid} ，此处填写的存储桶名称必须为此格式
    static String bucketName = "mirror-1301440453";

    public static String upload(File file, String key) {
        // 简单文件上传, 最大支持 5 GB, 适用于小文件上传, 建议 20M以下的文件使用该接口
        // 大文件上传请参照 API 文档高级 API 上传
        //file里面填写本地图片的位置 我这里是相对项目的位置，在项目下有src/test/demo.jpg这张图片
        File compressedFile;
        try {
            compressedFile = compressImage(file, 860, 480);
        } catch (Exception e) {
            compressedFile = file;
            LOGGER.warn("[MirrorTree]Failed to compress image: " + e);
        }
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, "/MirrorTree-Journey/screenshots/".concat(key), compressedFile);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        String etag = putObjectResult.getETag();  // 获取文件的 etag
        if (!compressedFile.delete()) {
            LOGGER.warn("[MirrorTree]Failed to delete local screenshot: " + compressedFile.getAbsolutePath());
        }
        return etag;
    }

    private static File compressImage(File inputFile, int width, int height) throws IOException {
        Thumbnails.of(inputFile)
                .size(width, height)
                .outputFormat("png")
                .toFiles(Rename.NO_CHANGE);
        return inputFile;
    }
}
