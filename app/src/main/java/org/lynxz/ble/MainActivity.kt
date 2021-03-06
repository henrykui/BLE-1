package org.lynxz.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AppCompatActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import org.lynxz.ble_lib.BleHelper
import org.lynxz.ble_lib.RelayCode
import org.lynxz.ble_lib.callbacks.OnRelayListener
import org.lynxz.ble_lib.config.BleConstant
import org.lynxz.ble_lib.showToast
import org.lynxz.ble_lib.util.Logger

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermission()

        with(BleHelper) {
            init(this@MainActivity)
            val keyInfo = "hello"
            val updatePara = updatePara(this@MainActivity, BleConstant.MODE_BOTH, keyInfo, null)
            Logger.d("更新ble参数结果: $updatePara  ${keyInfo.toByteArray().size}")

            var initMsg = "设置参数成功"
            when (updatePara) {
                RelayCode.ERR_PARA_INVALID -> initMsg = "设置失败: 参数不合法"
                RelayCode.ERR_NOT_SUPPORT -> initMsg = "设置失败: 系统不支持低功耗蓝牙(5.0以上)"
                RelayCode.ERR_BLUETOOTH_DISABLE -> initMsg = "设置失败: 请先开启蓝牙"
                RelayCode.ERR_GPS_DISABLE -> initMsg = "设置失败: 请先开启GPS定位"
                RelayCode.ERR_LACK_LOCATION_PERMISSION -> initMsg = "设置失败: 需要定位权限"
            }
            tv_info.text = initMsg

            onRelayListener = object : OnRelayListener {
                override fun onReceive(msg: String?) {
                    Logger.d("app收到蓝牙数据: $msg")
                    val msgObj = mHandler.obtainMessage(MSG_TYPE_SHOW_BLE_DATA)
                    msgObj.obj = msg
                    mHandler.sendMessage(msgObj)
                }

                override fun onScanBleDevices(bleDevices: BluetoothDevice?) {
                    super.onScanBleDevices(bleDevices)
                    val msgObj = mHandler.obtainMessage(MSG_TYPE_FIND_NEW_BLE)
                    msgObj.obj = bleDevices
                    mHandler.sendMessage(msgObj)
                }
            }
        }

        btn_start_scan.setOnClickListener { BleHelper.startScan() }
        btn_stop_scan.setOnClickListener { BleHelper.stopScan() }
        btn_start_advertising.setOnClickListener { BleHelper.startAdvertising() }
        btn_stop_advertising.setOnClickListener { BleHelper.stopAdvertising() }
        btn_send.setOnClickListener {
            val msg = edt_relay_info.text.toString()
            if (msg.isEmpty()) {
                showToast("请输入内容后重试")
            } else {
                BleHelper.relayData(msg)
            }
        }
    }

    val MSG_TYPE_SHOW_BLE_DATA = 100
    val MSG_TYPE_FIND_NEW_BLE = 101
    private val mHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_TYPE_SHOW_BLE_DATA -> tv_info.text = "收到数据:\n ${msg.obj}"
                MSG_TYPE_FIND_NEW_BLE -> {
                    val bleDeviceList = BleHelper.getBleDeviceList() ?: listOf()
                    tv_ble_list.text = "device list:"
                    for (dev in bleDeviceList) {
                        tv_ble_list.append("\n${dev.address} ${dev.name} ")
                    }
                }
            }
        }
    }

    /**
     * 扫描时需要定位权限
     * */
    private fun requestPermission() {
        val rxPermissions = RxPermissions(this)
        rxPermissions.request(Manifest.permission.ACCESS_FINE_LOCATION)
                .subscribe { accept ->
                    Logger.d("request result = $accept")
                    if (!accept) {
                        showToast("ble转传功能需要定位权限,否则可能扫描不到设备")
                    }
                }
    }
}
