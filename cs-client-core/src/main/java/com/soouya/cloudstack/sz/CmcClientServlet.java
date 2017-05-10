//package com.soouya.cloudstack.sz;
//
//import com.soouya.cloudstack.cmc.ClusterManagerClientFactory;
//import com.soouya.cloudstack.cmc.IClusterManagerClient;
//import org.apache.log4j.Logger;
//
//import javax.servlet.ServletException;
//import javax.servlet.http.HttpServlet;
//
///**
// * Created by xuyuli on 17-5-3.
// */
//public class CmcClientServlet extends HttpServlet{
//
//    private static Logger log = Logger.getLogger(CmcClientServlet.class);
//
//    @Override
//    public void init() throws ServletException {
//        super.init();
//        start();
//    }
//
//    public void start() {
//        try {
//            log.info("<<信息>> cmc开始启动......");
//            IClusterManagerClient client = ClusterManagerClientFactory.createClient();
//            client.register();
//            log.info("<<信息>> cmc启动成功......");
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            log.error("<<异常信息>> cmc启动出现异常信息：" + ex);
//        }
//
//    }
//
//
//}
