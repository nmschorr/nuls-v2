package io.nuls.pocbft.utils.manager;

import io.nuls.base.basic.AddressTool;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.storage.PubKeyStorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PubKeyManager {
    @Autowired
    private static PubKeyStorageService pubKeyService;

    public static void addPubKey(Chain chain, byte[] pubKey, String address){
        if(address == null){
            address = AddressTool.getAddressString(pubKey, chain.getChainId());
        }
        if(!chain.getPubKeyPo().getPackAddressPubKeyMap().containsKey(address)){
            chain.getPubKeyPo().getPackAddressPubKeyMap().put(address, pubKey);
            pubKeyService.save(chain.getPubKeyPo(), chain);
        }
    }

    public static List<byte[]> getPubKeyList(Chain chain, Set<String> addressList){
        List<byte[]> pubKeyList = new ArrayList<>();
        for (String address:addressList) {
            byte[] pubKey = chain.getPubKeyPo().getPackAddressPubKeyMap().get(address);
            if(pubKey != null){
                pubKeyList.add(pubKey);
            }
        }
        return pubKeyList;
    }
}
