package org.lynxz.ble_lib

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.support.v4.content.PermissionChecker
import org.lynxz.ble_lib.config.BleConstant
import org.lynxz.ble_lib.config.BlePara
import org.lynxz.ble_lib.util.Logger

/**
 * Created by lynxz on 19/06/2017.
 * 低功耗蓝牙帮助类
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
object BleHelper : BaseRelayHelper() {
    var mContext: Context? = null
    var mBleServiceIntent: Intent? = null
    var mBleBinder: BleService.BleBinder? = null
    var mEnable: Boolean = false // 接收/转传和广播功能是否可用
    val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Logger.d("service断开  $name")
            //todo 处理
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mBleBinder = service as BleService.BleBinder?
            mBleBinder?.onRelayListener = onRelayListener
            Logger.d("连接service成功 $name", TAG)
            if (mEnable) start()
        }
    }

    /**
     * 开启接收和转传功能
     * */
    fun start() {
        mEnable = true
        mBleBinder?.startAdvertising()
        mBleBinder?.startScanLeDevices()
    }

    /**
     * 停止扫描和广播
     * */
    fun stop() {
        mEnable = false
        mBleBinder?.stopAdvertising()
        mBleBinder?.stopScanLeDevices()
    }

    /**
     * 开始扫描ble设备
     * */
    fun startScan() {
        mEnable = true
        mBleBinder?.startScanLeDevices()
    }

    /**
     * 停止扫描ble设备
     * */
    fun stopScan() {
        mEnable = false
        mBleBinder?.stopScanLeDevices()
    }

    /**
     * 开启广播模式,可以作为 peripheral 设备
     * */
    fun startAdvertising() {
        mEnable = true
        mBleBinder?.startAdvertising()
    }

    /**
     * 停止广播模式
     * */
    fun stopAdvertising() {
        mEnable = false
        mBleBinder?.stopAdvertising()
    }

    /**
     * 获取扫描到的符合要求的ble设备列表
     * 若为可能返回null
     * */
    fun getBleDeviceList(): List<BluetoothDevice>? = mBleBinder?.getBleDeviceList()

    /**
     * 初始化context并连接service
     * */
    override fun init(context: Context): Int {
        if (mContext == null) {
            Logger.logLevel = Logger.DEBUG_LEVEL
            mContext = context.applicationContext
            mBleServiceIntent = Intent(mContext, BleService::class.java)
            context.bindService(mBleServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
        }
        return RelayCode.SUCCESS
    }

    /**
     * 参数设定
     *
     * @param mode ble模式
     *  * 双工 [BleConstant.MODE_BOTH][BleConstant.MODE_BOTH]
     *  * 只可收 [BleConstant.MODE_PERIPHERAL_ONLY][BleConstant.MODE_PERIPHERAL_ONLY]
     *  * 只可发 [BleConstant.MODE_BOTH][BleConstant.MODE_CENTRAL_ONLY]
     * @param desKey des加密密钥,默认值为 [BleConstant.DEFAULT_DES_KEY][BleConstant.DEFAULT_DES_KEY],传入null则使用默认值
     * @param adCharacteristicValue 默认值为 "" ,即表示不做过滤,此处禁止设置超过10字节的信息, 测试发现有些机型是14,16不等
     * */
    fun updatePara(context: Context?,
                   mode: Int = BleConstant.MODE_BOTH,
                   adCharacteristicValue: String = "",
                   desKey: String? = BleConstant.DEFAULT_DES_KEY): Int {
        var finalDesKey = desKey
        if (finalDesKey == null) {
            finalDesKey = BleConstant.DEFAULT_DES_KEY
        }

        if (context == null
                || mode < 0 || mode > 3
                || finalDesKey.length < 8
                || adCharacteristicValue.toByteArray().size > 10) {
            return RelayCode.ERR_PARA_INVALID
        }

        if (!context.isSupportBle()) {
            return RelayCode.ERR_NOT_SUPPORT
        } else if (!isBluetoothOn(context)) {
            return RelayCode.ERR_BLUETOOTH_DISABLE
        } else if (!context.isProviderEnable(LocationManager.GPS_PROVIDER)) {
            return RelayCode.ERR_GPS_DISABLE
        }

        // 需要定位权限
        val hasLocationPermission =
                PermissionChecker.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            return RelayCode.ERR_LACK_LOCATION_PERMISSION
        }
        updateBleService(mode, finalDesKey, adCharacteristicValue)
        return RelayCode.SUCCESS
    }

    /**
     * 按照用户设定的新参数来更新service动作
     * 各参数已判定为valid
     * */
    private fun updateBleService(mode: Int, finalDesKey: String, adCharacteristicValue: String) {
        if (BlePara.adCharacteristicValue != adCharacteristicValue) {
            BlePara.adCharacteristicValue = adCharacteristicValue
            stopAdvertising()
        }

        BlePara.desKey = finalDesKey

        if (BlePara.mode != mode) {
            BlePara.mode = mode
            stop()

            when (mode) {
                BleConstant.MODE_CENTRAL_ONLY -> startScan()
                BleConstant.MODE_PERIPHERAL_ONLY -> startAdvertising()
                BleConstant.MODE_BOTH -> start()
            }
        } else {
            startAdvertising()
        }
    }

    override fun relayData(msg: String?): Int {
        if (msg?.isEmpty() ?: true) {
            Logger.d("relay data fail as msg is empty")
            return RelayCode.ERR_PARA_INVALID
        }
        mBleBinder?.relayData(msg)
        return RelayCode.SUCCESS
    }
}