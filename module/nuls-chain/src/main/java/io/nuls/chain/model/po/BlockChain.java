package io.nuls.chain.model.po;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.basic.NulsOutputStreamBuffer;
import io.nuls.base.data.BaseNulsData;
import io.nuls.chain.model.tx.txdata.TxChain;
import io.nuls.chain.util.TimeUtil;
import io.nuls.core.exception.NulsException;
import io.nuls.core.parse.SerializeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author tangyi
 * @date 2018/11/7
 * @description
 */
public class BlockChain extends BaseNulsData {
    /**
     * 链序号
     * Chain ID
     */
    private int chainId;

    /**
     * 链名称
     * Chain name
     */
    private String chainName;

    /**
     * 地址类型（Nuls生态，其他）
     * Address type (Nuls ecology and others)
     */
    private String addressType;

    /**
     * 魔法参数（唯一）
     * Magic number (Unique)
     */
    private long magicNumber;

    /**
     * 是否支持外链资产流入
     * Whether to support the inflow of external assets
     */
    private boolean supportInflowAsset;

    /**
     * 最小可用节点数
     * Minimum number of available nodes
     */
    private int minAvailableNodeNum;

    /**
     * 单节点最小连接数
     * Single node minimum connection number
     */
    private int singleNodeMinConnectionNum;

    /**
     * 交易确认区块数
     * Transaction confirmation block counts
     */
    private int txConfirmedBlockNum;

    /**
     * 删除链时，设置为true
     * When deleting a chain, set to true
     */
    private boolean isDelete = false;

    /**
     * 创建时间
     * Create time
     */
    private long createTime;

    /**
     * 最后更新时间
     * Last update time
     */
    private long lastUpdateTime;

    /**
     * 注册链时使用的地址
     * The address used when registering the chain
     */
    private byte[] regAddress;

    /**
     * 注册链时的交易哈希
     * Transaction hash when registering the chain
     */
    private String regTxHash;

    /**
     * 注册链时添加的资产序号
     * The asset ID added when registering the chain
     */
    private int regAssetId;

    /**
     * 删除链时使用的地址
     * The address used when deleting the chain
     */
    private byte[] delAddress;

    /**
     * 删除链时的交易哈希
     * Transaction hash when deleting the chain
     */
    private String delTxHash;

    /**
     * 删除链时删除的资产序号
     * The asset ID deleted when deleting the chain
     */
    private int delAssetId;

    /**
     * 本链创建的所有资产，Key=chaiId_assetId
     * All assets created by this chain, Key=chaiId_assetId
     */
    List<String> selfAssetKeyList = new ArrayList<>();

    /**
     * 链上流通的所有资产，Key=chaiId_assetId
     * All assets circulating in the chain, Key=chaiId_assetId
     */
    List<String> totalAssetKeyList = new ArrayList<>();


    public void addCreateAssetId(String key) {
        selfAssetKeyList.add(key);
    }

    public void removeCreateAssetId(String key) {
        selfAssetKeyList.remove(key);
    }

    public void addCirculateAssetId(String key) {
        totalAssetKeyList.add(key);
    }

    public void removeCirculateAssetId(String key) {
        totalAssetKeyList.remove(key);
    }

    @Override
    protected void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.writeUint16(chainId);
        stream.writeString(chainName);
        stream.writeString(addressType);
        stream.writeUint32(magicNumber);
        stream.writeBoolean(supportInflowAsset);
        stream.writeUint32(minAvailableNodeNum);
        stream.writeUint32(singleNodeMinConnectionNum);
        stream.writeUint32(txConfirmedBlockNum);
        stream.writeBoolean(isDelete);
        stream.writeUint48(createTime);
        stream.writeUint48(lastUpdateTime);
        stream.writeBytesWithLength(regAddress);
        stream.writeString(regTxHash);
        stream.writeUint16(regAssetId);
        stream.writeBytesWithLength(delAddress);
        stream.writeString(delTxHash);
        stream.writeUint16(delAssetId);
        stream.writeUint16(selfAssetKeyList.size());
        for (String key : selfAssetKeyList) {
            stream.writeString(key);
        }
        stream.writeUint16(totalAssetKeyList.size());
        for (String key : totalAssetKeyList) {
            stream.writeString(key);
        }
    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) throws NulsException {
        this.chainId = byteBuffer.readUint16();
        this.chainName = byteBuffer.readString();
        this.addressType = byteBuffer.readString();
        this.magicNumber = byteBuffer.readInt32();
        this.supportInflowAsset = byteBuffer.readBoolean();
        this.minAvailableNodeNum = byteBuffer.readInt32();
        this.singleNodeMinConnectionNum = byteBuffer.readInt32();
        this.txConfirmedBlockNum = byteBuffer.readInt32();
        this.isDelete = byteBuffer.readBoolean();
        this.createTime = byteBuffer.readUint48();
        this.lastUpdateTime = byteBuffer.readUint48();
        this.regAddress = byteBuffer.readByLengthByte();
        this.regTxHash = byteBuffer.readString();
        this.regAssetId = byteBuffer.readUint16();
        this.delAddress = byteBuffer.readByLengthByte();
        this.delTxHash = byteBuffer.readString();
        this.delAssetId = byteBuffer.readUint16();
        int selfSize = byteBuffer.readUint16();
        for (int i = 0; i < selfSize; i++) {
            selfAssetKeyList.add(byteBuffer.readString());
        }
        int totalSize = byteBuffer.readUint16();
        for (int i = 0; i < totalSize; i++) {
            totalAssetKeyList.add(byteBuffer.readString());
        }
    }

    @Override
    public int size() {
        int size = 0;
        // chainId;
        size += SerializeUtils.sizeOfUint16();
        size += SerializeUtils.sizeOfString(chainName);
        size += SerializeUtils.sizeOfString(addressType);
        // magicNumber;
        size += SerializeUtils.sizeOfInt32();
        // supportInflowAsset;
        size += SerializeUtils.sizeOfBoolean();
        // minAvailableNodeNum;
        size += SerializeUtils.sizeOfInt32();
        // singleNodeMinConnectionNum;
        size += SerializeUtils.sizeOfInt32();
        // txConfirmedBlockNum;
        size += SerializeUtils.sizeOfInt32();
        // isDelete
        size += SerializeUtils.sizeOfBoolean();
        // createTime;
        size += SerializeUtils.sizeOfUint48();
        // lastUpdateTime;
        size += SerializeUtils.sizeOfUint48();
        size += SerializeUtils.sizeOfBytes(regAddress);
        //txHash
        size += SerializeUtils.sizeOfString(regTxHash);
        //regAssetId
        size += SerializeUtils.sizeOfInt16();
        size += SerializeUtils.sizeOfBytes(delAddress);
        //txHash
        size += SerializeUtils.sizeOfString(delTxHash);
        //delAssetId
        size += SerializeUtils.sizeOfInt16();

        size += SerializeUtils.sizeOfUint16();
        for (String key : selfAssetKeyList) {
            size += SerializeUtils.sizeOfString(key);
        }

        size += SerializeUtils.sizeOfUint16();
        for (String key : totalAssetKeyList) {
            size += SerializeUtils.sizeOfString(key);
        }
        return size;
    }

    public BlockChain() {
        super();
    }

    public BlockChain(TxChain txChain) {

        this.addressType = txChain.getAddressType();
        this.chainId = txChain.getChainId();
        this.magicNumber = txChain.getMagicNumber();
        this.minAvailableNodeNum = txChain.getMinAvailableNodeNum();
        this.chainName = txChain.getName();
        this.singleNodeMinConnectionNum = txChain.getSingleNodeMinConnectionNum();
        this.supportInflowAsset = txChain.isSupportInflowAsset();
    }

    public byte[] parseToTransaction(Asset asset) throws IOException {
        TxChain txChain = new TxChain();

        txChain.setAddressType(this.addressType);
        txChain.setChainId(this.chainId);
        txChain.setMagicNumber(this.magicNumber);
        txChain.setMinAvailableNodeNum(this.minAvailableNodeNum);
        txChain.setName(this.chainName);
        txChain.setSingleNodeMinConnectionNum(this.singleNodeMinConnectionNum);
        txChain.setSupportInflowAsset(this.supportInflowAsset);
        txChain.setAddress(asset.getAddress());

        txChain.setAssetId(asset.getAssetId());
        txChain.setSymbol(asset.getSymbol());
        txChain.setAssetName(asset.getAssetName());
        txChain.setDepositNuls(asset.getDepositNuls());
        txChain.setInitNumber(asset.getInitNumber());
        txChain.setDecimalPlaces(asset.getDecimalPlaces());
        return txChain.serialize();
    }

    public int getChainId() {
        return chainId;
    }

    public void setChainId(int chainId) {
        this.chainId = chainId;
    }

    public String getChainName() {
        return chainName;
    }

    public void setChainName(String chainName) {
        this.chainName = chainName;
    }

    public String getAddressType() {
        return addressType;
    }

    public void setAddressType(String addressType) {
        this.addressType = addressType;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(long magicNumber) {
        this.magicNumber = magicNumber;
    }

    public boolean isSupportInflowAsset() {
        return supportInflowAsset;
    }

    public void setSupportInflowAsset(boolean supportInflowAsset) {
        this.supportInflowAsset = supportInflowAsset;
    }

    public int getMinAvailableNodeNum() {
        return minAvailableNodeNum;
    }

    public void setMinAvailableNodeNum(int minAvailableNodeNum) {
        this.minAvailableNodeNum = minAvailableNodeNum;
    }

    public int getSingleNodeMinConnectionNum() {
        return singleNodeMinConnectionNum;
    }

    public void setSingleNodeMinConnectionNum(int singleNodeMinConnectionNum) {
        this.singleNodeMinConnectionNum = singleNodeMinConnectionNum;
    }

    public int getTxConfirmedBlockNum() {
        return txConfirmedBlockNum;
    }

    public void setTxConfirmedBlockNum(int txConfirmedBlockNum) {
        this.txConfirmedBlockNum = txConfirmedBlockNum;
    }

    public boolean isDelete() {
        return isDelete;
    }

    public void setDelete(boolean delete) {
        isDelete = delete;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public byte[] getRegAddress() {
        return regAddress;
    }

    public void setRegAddress(byte[] regAddress) {
        this.regAddress = regAddress;
    }

    public String getRegTxHash() {
        return regTxHash;
    }

    public void setRegTxHash(String regTxHash) {
        this.regTxHash = regTxHash;
    }

    public int getRegAssetId() {
        return regAssetId;
    }

    public void setRegAssetId(int regAssetId) {
        this.regAssetId = regAssetId;
    }

    public byte[] getDelAddress() {
        return delAddress;
    }

    public void setDelAddress(byte[] delAddress) {
        this.delAddress = delAddress;
    }

    public String getDelTxHash() {
        return delTxHash;
    }

    public void setDelTxHash(String delTxHash) {
        this.delTxHash = delTxHash;
    }

    public int getDelAssetId() {
        return delAssetId;
    }

    public void setDelAssetId(int delAssetId) {
        this.delAssetId = delAssetId;
    }

    public List<String> getSelfAssetKeyList() {
        return selfAssetKeyList;
    }

    public void setSelfAssetKeyList(List<String> selfAssetKeyList) {
        this.selfAssetKeyList = selfAssetKeyList;
    }

    public List<String> getTotalAssetKeyList() {
        return totalAssetKeyList;
    }

    public void setTotalAssetKeyList(List<String> totalAssetKeyList) {
        this.totalAssetKeyList = totalAssetKeyList;
    }

    public void map2pojo(Map<String,Object> map){
        this.setAddressType(String.valueOf(map.get("addressType")));
        this.setChainId(Integer.valueOf(map.get("chainId").toString()));
        this.setChainName(String.valueOf(map.get("chainName")));
        this.setMagicNumber(Long.valueOf(map.get("magicNumber").toString()));
        this.setMinAvailableNodeNum(Integer.valueOf(map.get("minAvailableNodeNum").toString()));
        this.setSingleNodeMinConnectionNum(Integer.valueOf(map.get("singleNodeMinConnectionNum").toString()));
        this.setTxConfirmedBlockNum(Integer.valueOf(map.get("txConfirmedBlockNum").toString()));
        this.setRegAddress(AddressTool.getAddress(map.get("address").toString()));
        this.setCreateTime(TimeUtil.getCurrentTime());
    }
}