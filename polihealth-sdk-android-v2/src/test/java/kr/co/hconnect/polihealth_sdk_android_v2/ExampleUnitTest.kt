package kr.co.hconnect.polihealth_sdk_android_v2

import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.service.sleep.SleepApiService
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun protocol01API() = runBlocking {
        PoliClient.init(
            "https://mapi-stg.health-on.co.kr", "3270e7da-55b1-4dd4-abb9-5c71295b849b",
            "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpbmZyYSI6IkhlYWx0aE9uLVN0YWdpbmciLCJjbGllbnQtaWQiOiIzMjcwZTdkYS01NWIxLTRkZDQtYWJiOS01YzcxMjk1Yjg0OWIifQ.u0rBK-2t3l4RZ113EzudZsKb0Us9PEtiPcFDBv--gYdJf9yZJQOpo41XqzbgSdDa6Z1VDrgZXiOkIZOTeeaEYA"
        )

        delay(100)

        val response = SleepApiService().sendStartSleep()
        print(response)
    }

    @Test
    fun testt() = runBlocking {
//        PoliClient.init( "\"https://mapi-stg.health-on.co.kr\"", "\"3270e7da-55b1-4dd4-abb9-5c71295b849b\"",
//            "\"eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpbmZyYSI6IkhlYWx0aE9uLVN0YWdpbmciLCJjbGllbnQtaWQiOiIzMjcwZTdkYS01NWIxLTRkZDQtYWJiOS01YzcxMjk1Yjg0OWIifQ.u0rBK-2t3l4RZ113EzudZsKb0Us9PEtiPcFDBv--gYdJf9yZJQOpo41XqzbgSdDa6Z1VDrgZXiOkIZOTeeaEYA\""
//        )

        delay(100)

        PoliClient.init("https://mapi-stg.health-on.co.kr", "", "")

        val response = PoliClient.client.get(
            "https://669db2b59a1bda3680042b98.mockapi.io/api/mock1/mock1"
        )

    }
}