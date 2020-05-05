package cn.spx.blogDeploy2Cos

import com.google.gson.Gson
import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.auth.COSCredentials
import com.qcloud.cos.model.PutObjectRequest
import com.qcloud.cos.model.PutObjectResult
import com.qcloud.cos.region.Region
import groovy.util.logging.Slf4j
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.FileSet;

@Slf4j
class CosOpt{
    //腾讯Cos相关信息
    final String secretId = "tx_secretId";
    final String secretKey = "tx_key";
    final String bucketName = "cos_buckName"
    //blog目录
    final  String blogFolder = '/root/blog'
    final  String blogPublicFolder =  blogFolder + '/public/'
    final Gson gson = new Gson()
    COSCredentials cred = new BasicCOSCredentials(secretId, secretKey)
    Region region = new Region("ap-shanghai")
    ClientConfig clientConfig = new ClientConfig(region)
    COSClient cosClient = new COSClient(cred, clientConfig)
    //保证同一时间只能有一个线程操作
    private  volatile Integer RUNING_FLAG = 0
    static CosOpt cosOpt;
    private CosOpt(){
    }

    static getCosOpt(){
        if(cosOpt){
            return cosOpt
        }else {
            cosOpt = new CosOpt()
            return cosOpt
        }
    }

    def pullAndHugoExec(){
        //我把hugo二进制程序直接放到了blog目录下，所以直接这么写了
        String command = 'sh -c  "cd ${blogFolder} && git pull && ./hugo"'.toString()
        //windows下命令
        if(System.getProperty("os.name").toLowerCase().startsWith("win")) {
            command = "cmd /c cd ${blogFolder} && git pull && hugo".toString()
        }
        command.execute()
    }

    def pushObj2Cos(String  fileNmae,File localFile){
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fileNmae,localFile)
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest)
        log.info("上传结果:{}",gson.toJson(putObjectResult))
    }

    def pushBlog2Cos(){
        def tmpDir = System.getProperty("java.io.tmpdir")
        def zipFileName = 'zip_blog.zip'
        def tmpZipZipfilePatch = tmpDir + 'zip_blog.zip'
       /* new File(blogFolder)
                .eachFileRecurse(FileType.FILES){
                    fileList.add(it)
            }*/
        Date startDate = new Date()
        zipFile(blogPublicFolder,tmpZipZipfilePatch)
        Date zipEndDate = new Date()
        pushObj2Cos(zipFileName,new File(tmpZipZipfilePatch))
        log.info("Done,zip time:${zipEndDate.getTime() - startDate.getTime()},push2Cos Time ${new Date().getTime() - zipEndDate.getTime()}")
        //结束后删除public目录
        deleteDir(blogPublicFolder)
    }
    static def deleteDir(String dirPath){
        File f = new File(dirPath)
        f.deleteDir()
    }

    static def zipFile( String srcPath, String zipFilePath) {
        File zipFile = new File(zipFilePath);
        File srcdir = new File(srcPath);
        if (zipFile.exists()) {
            zipFile.delete()
        }
        for (def i = 1;i<21;i++) {
            if (!srcdir.exists()) {
                log.info("源目录${srcPath}不存在1s后重试,times:${i}")
                Thread.currentThread().sleep(2000)
            }else {
                break
            }
            if(i==20){
                throw  new Exception('源文件不存在结束')
            }
            srcdir = new File(srcPath)
        }
        Project prj = new Project()
        Zip zip = new Zip()
        zip.setProject(prj)
        zip.setDestFile(zipFile)
        FileSet fileSet = new FileSet()
        fileSet.setProject(prj)
        fileSet.setDir(srcdir)
        fileSet.setIncludes("**/*.*");
        //fileSet.setExcludes(...); 排除哪些文件或文件夹
        zip.addFileset(fileSet)
        zip.execute()
    }

    static runInNewThread(){
        new Thread({
            CosOpt opt = getCosOpt()
            while (1) {
                if (opt.RUNING_FLAG) {
                    log.warn("程序正在执行，1秒后重试")
                    Thread.currentThread().sleep(1000)
                    continue
                }
                break
            }
            opt.RUNING_FLAG = 1
            opt.pullAndHugoExec()
            //不调sync可能会提示不存在文件
            fileSystemSync()
            opt.pushBlog2Cos()
            fileSystemSync()
            opt.RUNING_FLAG = 0
        }).start()
    }

    static fileSystemSync(){
        //如果有sync命令就执行，没有就延时1秒(win下可能没sync）
        try {
            'sync'.execute()
        }catch (Exception e) {
            log.error('sync error',e)
            Thread.currentThread().sleep(1000)
        }
        Thread.currentThread().sleep(1)
    }

    public static void main(String[] args) {
        runInNewThread()
    }
}