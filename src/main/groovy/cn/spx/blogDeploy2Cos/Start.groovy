package cn.spx.blogDeploy2Cos

import groovy.util.logging.Slf4j
import spark.Spark

@Slf4j
class Start {
    //github webhooks scertKey,如果没有设置留空
    static  final String WEBHOOK_SCERT = ''
    static void main(String[] args) {
        Spark.port(8088)
        Spark.threadPool(4,2,30000)
        Spark.init()
        Spark.post("/blogWebhook", (req, res) -> {
            def headersStr = req.headers().collect{it+':'+ req.headers(it)}.join(",")
            log.info("get request,headers:[${headersStr}],body:${req.body().replaceAll("[\r|\n]","\\\\n")}")
            if(WEBHOOK_SCERT){
                def jsonBody = req.body()
                def signature = req.headers('X-Hub-Signature')
                if ([jsonBody,signature].every() && verifyWebhookScert(WEBHOOK_SCERT,signature,jsonBody)) {
                    log.info('web hook verify secret pass')
                }else{
                    log.warn('web hook verify secret fail,sign:{},jsonBody:{}',signature,jsonBody.replaceAll("[\r\n]","\\\\n"))
                    res.status(404)
                    res.body("not found")
                    return res.body()
                }
            }
            //新开线程执行操作，然后返回success
            CosOpt.runInNewThread()
            res.status(200)
            res.body("success")
            return res.body()
        })
    }

    //校验github secret
    static Boolean verifyWebhookScert(String scert,String signature,String postBody){
        //github的signature是sha1=开头的
        signature  ==  ('sha1=' + Utils.hmac(scert,postBody))
    }

}
