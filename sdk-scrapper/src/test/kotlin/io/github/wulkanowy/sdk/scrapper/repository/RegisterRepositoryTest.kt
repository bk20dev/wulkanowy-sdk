package io.github.wulkanowy.sdk.scrapper.repository

import io.github.wulkanowy.sdk.scrapper.BaseLocalTest
import io.github.wulkanowy.sdk.scrapper.Scrapper
import io.github.wulkanowy.sdk.scrapper.exception.VulcanException
import io.github.wulkanowy.sdk.scrapper.interceptor.ErrorInterceptorTest
import io.github.wulkanowy.sdk.scrapper.login.LoginHelper
import io.github.wulkanowy.sdk.scrapper.login.LoginTest
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator
import io.github.wulkanowy.sdk.scrapper.register.RegisterStudent
import io.github.wulkanowy.sdk.scrapper.register.RegisterTest
import io.github.wulkanowy.sdk.scrapper.service.LoginService
import io.github.wulkanowy.sdk.scrapper.service.RegisterService
import io.github.wulkanowy.sdk.scrapper.service.StudentService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.CookieManager

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterRepositoryTest : BaseLocalTest() {

    private val normal by lazy { getRegisterRepository("Default") }

    private fun getRegisterRepository(symbol: String): RegisterRepository {
        return RegisterRepository(
            startSymbol = symbol,
            email = "jan@fakelog.cf",
            password = "jan123",
            loginHelper = LoginHelper(
                loginType = Scrapper.LoginType.STANDARD,
                schema = "http",
                host = "fakelog.localhost:3000",
                symbol = symbol,
                cookies = CookieManager(),
                api = getService(LoginService::class.java, "http://fakelog.localhost:3000/"),
            ),
            register = getService(
                service = RegisterService::class.java,
                url = "http://fakelog.localhost:3000/",
                okHttp = getOkHttp(errorInterceptor = false, autoLoginInterceptorOn = false),
            ),
            student = getService(service = StudentService::class.java, html = false),
            url = UrlGenerator("http", "fakelog.localhost:3000", symbol, ""),
        )
    }

    @Test
    fun normalLogin_one() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success.html", LoginTest::class.java)

            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("WitrynaUcznia.html", RegisterTest::class.java)
            enqueue("UczenCache.json", RegisterTest::class.java)
            enqueue("UczenDziennik-no-semester.json", RegisterTest::class.java)

            repeat(4) { // 4x symbol
                enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            }
            start(3000)
        }

        val user = getRegisterRepository("Default").getUserSubjects()
        val school = user.symbols[0].schools[0]
        val students = school.subjects.filterIsInstance<RegisterStudent>()

        with(school) {
            assertEquals("123456", schoolId)
            assertEquals("Fake123456", schoolShortName)
        }
        assertEquals(1, students.size)
        assertEquals(2, students[0].semesters.size)
    }

    @Test
    fun normalLogin_semesters() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success.html", LoginTest::class.java)

            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("WitrynaUcznia.html", RegisterTest::class.java)
            enqueue("UczenCache.json", RegisterTest::class.java)
            enqueue("UczenDziennik.json", RegisterTest::class.java)

            repeat(4) { // 4x symbol
                enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            }
            start(3000)
        }

        val user = getRegisterRepository("Default").getUserSubjects()
        val school = user.symbols[0].schools[0]
        val students = school.subjects.filterIsInstance<RegisterStudent>()

        with(school) {
            assertEquals("123456", schoolId)
            assertEquals("Fake123456", schoolShortName)
        }
        assertEquals(6, students[0].semesters.size)
        assertEquals(2, students.size)
        assertEquals(6, students[1].semesters.size)
    }

    @Test
    fun normalLogin_triple() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success-triple.html", LoginTest::class.java)

            repeat(3) {
                enqueue("LoginPage-standard.html", LoginTest::class.java)
                enqueue("WitrynaUcznia.html", RegisterTest::class.java)
                enqueue("UczenCache.json", RegisterTest::class.java)
                enqueue("UczenDziennik-no-semester.json", RegisterTest::class.java)
            }

            repeat(4) { // 4x symbol
                enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            }
            start(3000)
        }

        val user = getRegisterRepository("Default").getUserSubjects()
        val schools = user.symbols[0].schools
        assertEquals(3, schools.size)

        with(schools[0]) {
            assertEquals("000788", schoolId)
            assertEquals("ZST-CKZiU", schoolShortName)
        }

        with(schools[1]) {
            assertEquals("004355", schoolId)
            assertEquals("ZSET", schoolShortName)
        }

        with(schools[2]) {
            assertEquals("016636", schoolId)
            assertEquals("G7 Wulkanowo", schoolShortName)
        }
    }

    @Test
    fun normalLogin_temporarilyOff() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success-triple.html", LoginTest::class.java)

            repeat(2) {
                enqueue("WitrynaUcznia.html", RegisterTest::class.java)
                enqueue("UczenCache.json", RegisterTest::class.java)
                enqueue("UczenDziennik-no-semester.json", RegisterTest::class.java)
            }

            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("AplikacjaCzasowoWyłączona.html", ErrorInterceptorTest::class.java)

            repeat(4) { // 4x symbol
                enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            }
            start(3000)
        }

        val user = getRegisterRepository("Default").getUserSubjects()
        val students = user.symbols
            .flatMap { it.schools }
            .flatMap { it.subjects }
            .filterIsInstance<RegisterStudent>()

        assertEquals(2, students.size)
    }

    @Test
    fun normalVulcanException() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Offline.html", ErrorInterceptorTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            start(3000)
        }

        val res = normal.getUserSubjects().symbols
        assertEquals(5, res.size)
        assertEquals(
            "Wystąpił nieoczekiwany błąd. Wystąpił błąd aplikacji. Prosimy zalogować się ponownie. Jeśli problem będzie się powtarzał, prosimy o kontakt z serwisem.",
            res[1].error?.message,
        )
        assertEquals(VulcanException::class.java, res[1].error!!::class.java)
    }

    @Test
    fun filterSymbolsWithSpaces() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            repeat(5) {
                enqueue("Login-success-old.html", LoginTest::class.java)
            }

            start(3000)
        }

        val user = normal.getUserSubjects().symbols
            .flatMap { it.schools }
            .flatMap { it.subjects }

        assertEquals(0, user.size)
    }

    @Test
    fun normalizeInvalidSymbol_default() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Offline.html", ErrorInterceptorTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)

            start(3000)
        }

        val res = getRegisterRepository("Default").getUserSubjects()
        assertEquals(5, res.symbols.size)
        assertEquals(
            "Wystąpił nieoczekiwany błąd. Wystąpił błąd aplikacji. Prosimy zalogować się ponownie. Jeśli problem będzie się powtarzał, prosimy o kontakt z serwisem.",
            res.symbols[1].error?.message,
        )
        assertTrue(res.symbols[1].error is VulcanException)
        assertEquals("/Default/Account/LogOn", server.takeRequest().path)
    }

    @Test
    fun normalizeInvalidSymbol_custom() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Offline.html", ErrorInterceptorTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)

            start(3000)
        }

        val res = getRegisterRepository(" Rzeszów + ").getUserSubjects()
        assertEquals(5, res.symbols.size)
        assertTrue(res.symbols[1].error is VulcanException)
        assertEquals(
            "Wystąpił nieoczekiwany błąd. Wystąpił błąd aplikacji. Prosimy zalogować się ponownie. Jeśli problem będzie się powtarzał, prosimy o kontakt z serwisem.",
            res.symbols[1].error?.message,
        )
        assertEquals("/rzeszow/Account/LogOn", server.takeRequest().path)
    }

    @Test
    fun normalizeInvalidSymbol_trimMultipleSpaces() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Offline.html", ErrorInterceptorTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)

            start(3000)
        }

        val res = getRegisterRepository(" Niepoprawny    symbol no ale + ").getUserSubjects()
        assertTrue(res.symbols[1].error is VulcanException)
        assertEquals(
            "Wystąpił nieoczekiwany błąd. Wystąpił błąd aplikacji. Prosimy zalogować się ponownie. Jeśli problem będzie się powtarzał, prosimy o kontakt z serwisem.",
            res.symbols[1].error?.message,
        )

        assertEquals("/niepoprawnysymbolnoale/Account/LogOn", server.takeRequest().path)
    }

    @Test
    fun normalizeInvalidSymbol_emptyFallback() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Offline.html", ErrorInterceptorTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)

            start(3000)
        }

        val res = getRegisterRepository(" + ").getUserSubjects()
        assertEquals(5, res.symbols.size)
        assertTrue(res.symbols[1].error is VulcanException)
        assertEquals(
            "Wystąpił nieoczekiwany błąd. Wystąpił błąd aplikacji. Prosimy zalogować się ponownie. Jeśli problem będzie się powtarzał, prosimy o kontakt z serwisem.",
            res.symbols[1].error?.message,
        )
        assertEquals("/Default/Account/LogOn", server.takeRequest().path)
    }

    @Test
    fun normalizeInvalidSymbol_digits() = runTest {
        with(server) {
            enqueue("LoginPage-standard.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)

            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Offline.html", ErrorInterceptorTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)
            enqueue("Logowanie-brak-dostepu.html", LoginTest::class.java)

            start(3000)
        }

        val res = getRegisterRepository("Default").getUserSubjects()
        assertEquals(5, res.symbols.size)
        assertTrue(res.symbols[1].error is VulcanException)
        assertEquals(
            "Wystąpił nieoczekiwany błąd. Wystąpił błąd aplikacji. Prosimy zalogować się ponownie. Jeśli problem będzie się powtarzał, prosimy o kontakt z serwisem.",
            res.symbols[1].error?.message,
        )
        assertEquals("/Default/Account/LogOn", server.takeRequest().path)
        assertEquals(true, server.takeRequest().path?.startsWith("/Account/LogOn?ReturnUrl=%2FDefault"))
        assertEquals("/powiatwulkanowy/LoginEndpoint.aspx", server.takeRequest().path)
        assertEquals("/glubczyce2/LoginEndpoint.aspx", server.takeRequest().path)
    }
}
