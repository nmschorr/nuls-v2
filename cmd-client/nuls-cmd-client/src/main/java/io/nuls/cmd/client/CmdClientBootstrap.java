package io.nuls.cmd.client;

import ch.qos.logback.classic.Level;
import io.nuls.api.provider.Provider;
import io.nuls.api.provider.ServiceManager;
import io.nuls.rpc.info.HostInfo;
import io.nuls.rpc.modulebootstrap.NulsRpcModuleBootstrap;
import io.nuls.tools.core.config.ConfigurationLoader;
import io.nuls.tools.log.Log;
import io.nuls.tools.log.logback.LoggerBuilder;

/**
 * @Author: zhoulijun
 * @Time: 2019-03-06 17:07
 * @Description: 功能描述
 */
public class CmdClientBootstrap {

    public static void main(String[] args) {
        NulsRpcModuleBootstrap.printLogo("/cli-logo");
        Log.BASIC_LOGGER = LoggerBuilder.getLogger(Log.BASIC_NAME, Level.ERROR);
        if (args == null || args.length == 0) {
            args = new String[]{"ws://" + HostInfo.getLocalIP() + ":8887/ws","0"};
        }else{
            args = new String[]{args[0],"0"};
        }
        ConfigurationLoader configurationLoader = new ConfigurationLoader();
        configurationLoader.load();
        Provider.ProviderType providerType = Provider.ProviderType.valueOf(configurationLoader.getValue("providerType"));
        int defaultChainId = Integer.parseInt(configurationLoader.getValue("chainId"));
        ServiceManager.init(defaultChainId,providerType);
        try {
            NulsRpcModuleBootstrap.run("io.nuls.cmd.client",args);
        }catch (Exception e){
            Log.error("module start fail {}",e.getMessage());
        }
    }

}
