package io.github.wulkanowy.sdk.scrapper.repository

import com.migcomponents.migbase64.Base64
import io.github.wulkanowy.sdk.scrapper.Scrapper
import io.github.wulkanowy.sdk.scrapper.exception.ScrapperException
import io.github.wulkanowy.sdk.scrapper.exception.TemporarilyDisabledException
import io.github.wulkanowy.sdk.scrapper.getNormalizedSymbol
import io.github.wulkanowy.sdk.scrapper.getScriptParam
import io.github.wulkanowy.sdk.scrapper.interceptor.handleErrors
import io.github.wulkanowy.sdk.scrapper.login.AccountPermissionException
import io.github.wulkanowy.sdk.scrapper.login.CertificateResponse
import io.github.wulkanowy.sdk.scrapper.login.LoginHelper
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator
import io.github.wulkanowy.sdk.scrapper.register.AuthInfo
import io.github.wulkanowy.sdk.scrapper.register.Diary
import io.github.wulkanowy.sdk.scrapper.register.HomePageResponse
import io.github.wulkanowy.sdk.scrapper.register.PermissionUnit
import io.github.wulkanowy.sdk.scrapper.register.Permissions
import io.github.wulkanowy.sdk.scrapper.register.RegisterStudent
import io.github.wulkanowy.sdk.scrapper.register.RegisterSymbol
import io.github.wulkanowy.sdk.scrapper.register.RegisterUnit
import io.github.wulkanowy.sdk.scrapper.register.RegisterUser
import io.github.wulkanowy.sdk.scrapper.register.Student
import io.github.wulkanowy.sdk.scrapper.register.toSemesters
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository.Companion.SELECTOR_ADFS
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository.Companion.SELECTOR_ADFS_CARDS
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository.Companion.SELECTOR_ADFS_LIGHT
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository.Companion.SELECTOR_STANDARD
import io.github.wulkanowy.sdk.scrapper.service.RegisterService
import io.github.wulkanowy.sdk.scrapper.service.StudentService
import io.github.wulkanowy.sdk.scrapper.timetable.CacheResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

class RegisterRepository(
    private val startSymbol: String,
    private val email: String,
    private val password: String,
    private val loginHelper: LoginHelper,
    private val register: RegisterService,
    private val student: StudentService,
    private val url: UrlGenerator
) {

    companion object {
        @JvmStatic
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun getStudents(): List<Student> {
        val user = getUserSubjects()

        return user.symbols.flatMap { symbol ->
            symbol.error?.takeIf { it !is AccountPermissionException }?.let { throw it }

            symbol.schools.flatMap { unit ->
                unit.error?.takeIf { it !is TemporarilyDisabledException }?.let { throw it }

                unit.subjects.filterIsInstance<RegisterStudent>().map { student ->
                    Student(
                        email = user.email,
                        userLogin = user.login,
                        userName = symbol.userName,
                        userLoginId = unit.userLoginId,
                        symbol = symbol.symbol,
                        studentId = student.studentId,
                        studentName = student.studentName,
                        studentSecondName = student.studentSecondName,
                        studentSurname = student.studentSurname,
                        schoolSymbol = unit.schoolId,
                        schoolShortName = unit.schoolShortName,
                        schoolName = unit.schoolName,
                        className = student.className,
                        classId = student.classId,
                        baseUrl = user.baseUrl,
                        loginType = user.loginType,
                        isParent = student.isParent,
                        semesters = student.semesters,
                    )
                }
            }
        }
    }

    suspend fun getUserSubjects(): RegisterUser {
        val symbolLoginType = getLoginType(startSymbol.getNormalizedSymbol())
        val certificateResponse = getCert(symbolLoginType)
        val (login, emailAddress, symbols) = certificateResponse.toCertificateValues()

        return RegisterUser(
            email = emailAddress.ifBlank { email },
            login = getNormalizedLogin(login, emailAddress).ifBlank { email },
            baseUrl = url.generate(UrlGenerator.Site.BASE),
            loginType = symbolLoginType,
            symbols = getRegisterSymbols(
                symbols = symbols,
                loginCert = certificateResponse,
            ),
        )
    }

    private suspend fun getRegisterSymbols(
        symbols: List<String>,
        loginCert: CertificateResponse,
    ): List<RegisterSymbol> = symbols.map { symbol ->
        val homeResponse = runCatching {
            val res = loginHelper.sendCertificate(
                cert = loginCert,
                email = email,
                url = loginCert.action.replace(
                    oldValue = startSymbol.getNormalizedSymbol(), // improve this: for random inputs may generate random errors :)
                    newValue = symbol
                ),
            )
            url.symbol = symbol
            res
        }
        RegisterSymbol(
            symbol = symbol,
            error = homeResponse.exceptionOrNull(),
            userName = homeResponse.getOrNull()
                .getUserNameFromUserData(),
            schools = homeResponse.getOrNull()
                .toPermissions()
                .toUnitsMap()
                .getRegisterUnits()
        )
    }

    private suspend fun Map<PermissionUnit, AuthInfo?>.getRegisterUnits(): List<RegisterUnit> {
        return map { (unit, authInfo) ->
            url.schoolId = unit.symbol

            val cacheAndDiaries = runCatching {
                getStudentCache() to getStudentDiaries()
            }

            RegisterUnit(
                userLoginId = requireNotNull(authInfo?.loginId),
                schoolId = unit.symbol,
                schoolName = unit.name,
                schoolShortName = unit.short,
                error = cacheAndDiaries.exceptionOrNull(),
                subjects = cacheAndDiaries.getOrNull()?.let { (cache, diaries) ->
                    diaries.filterDiaries().map { diary ->
                        val classId = diary.semesters?.firstOrNull()?.classId ?: 0
                        RegisterStudent(
                            studentId = diary.studentId,
                            studentName = diary.studentName.trim(),
                            studentSecondName = diary.studentSecondName.orEmpty(),
                            studentSurname = diary.studentSurname,
                            className = diary.symbol.orEmpty(),
                            classId = classId,
                            isParent = cache?.isParent == true,
                            semesters = diaries.toSemesters(
                                studentId = diary.studentId,
                                classId = classId,
                                unitId = unit.id,
                            ),
                        )
                    }
                }.orEmpty()
            )
        }
    }

    private suspend fun getLoginType(symbol: String): Scrapper.LoginType {
        val urlGenerator = url.also { it.symbol = symbol }
        val page = register.getFormType(urlGenerator.generate(UrlGenerator.Site.LOGIN) + "Account/LogOn").page
        return when {
            page.select(SELECTOR_STANDARD).isNotEmpty() -> Scrapper.LoginType.STANDARD
            page.select(SELECTOR_ADFS).isNotEmpty() -> Scrapper.LoginType.ADFS
            page.select(SELECTOR_ADFS_LIGHT).isNotEmpty() -> {
                page.selectFirst("form")?.attr("action").orEmpty().run {
                    when {
                        contains("cufs.edu.gdansk.pl") -> Scrapper.LoginType.ADFS // for compatibility with old accounts
                        contains("cufs.edu.lublin.eu") -> Scrapper.LoginType.ADFSLightCufs
                        startsWith("/LoginPage.aspx") -> Scrapper.LoginType.ADFSLight
                        startsWith("/${urlGenerator.symbol}/LoginPage.aspx") -> Scrapper.LoginType.ADFSLightScoped
                        else -> throw ScrapperException("Nieznany typ dziennika ADFS: ${page.text()}")
                    }
                }
            }

            page.select(SELECTOR_ADFS_CARDS).isNotEmpty() -> Scrapper.LoginType.ADFSCards
            else -> throw ScrapperException("Nieznany typ dziennika: ${page.text()}")
        }
    }

    private fun getNormalizedLogin(login: String, email: String): String = when (email) {
        login.lowercase() -> email // AttributeName="name" contains entered email in standard login
        else -> login
    }

    private suspend fun getCert(symbolLoginType: Scrapper.LoginType): CertificateResponse {
        logger.debug("Register login type: $symbolLoginType")
        return loginHelper
            .apply { loginType = symbolLoginType }
            .sendCredentials(email, password)
    }

    private fun CertificateResponse.toCertificateValues(): Triple<String, String, List<String>> {
        val cert = Jsoup.parse(wresult.replace(":", ""), "", Parser.xmlParser())

        val symbols = cert.select("[AttributeName$=\"Instance\"] samlAttributeValue")
        val emailAddress = cert.select("[AttributeName=\"emailaddress\"] samlAttributeValue").text()
        val login = cert.select("[AttributeName=\"name\"] samlAttributeValue").text()

        return Triple(login, emailAddress, symbols.toNormalizedSymbols())
    }

    private fun Elements.toNormalizedSymbols(): List<String> = this
        .map { it.text().trim() }
        .apply { logger.debug("$this") }
        .filter { it.matches("[a-zA-Z0-9]*".toRegex()) } // early filter invalid symbols
        .filter { it != "Default" }

    private fun HomePageResponse?.getUserNameFromUserData(): String {
        val data = this?.userData ?: return ""

        val adfsName = data.substringBefore(" (", "")
        val standardName = data.substringBefore(" - ", adfsName)

        return standardName.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun HomePageResponse?.toPermissions(): Permissions? {
        val base64 = getScriptParam("permissions", this?.document.toString()).substringBefore("|")
        return Base64.decode(base64).toString(StandardCharsets.UTF_8).takeIf { it.isNotBlank() }?.let {
            json.decodeFromString<Permissions>(it)
        }
    }

    private fun Permissions?.toUnitsMap(): Map<PermissionUnit, AuthInfo?> {
        return this?.units?.associateWith { unit ->
            authInfos.find { it.unitId == unit.id }
        }?.filterNot { (_, authInfo) ->
            authInfo?.studentIds.isNullOrEmpty() && authInfo?.parentIds.isNullOrEmpty() // todo: change to support teachers
        }.orEmpty()
    }

    private suspend fun getStudentCache(): CacheResponse? {
        val startPage = student.getStart(url.generate(UrlGenerator.Site.STUDENT) + "Start")

        return student.getUserCache(
            url.generate(UrlGenerator.Site.STUDENT) + "UczenCache.mvc/Get",
            getScriptParam("antiForgeryToken", startPage),
            getScriptParam("appGuid", startPage),
            getScriptParam("version", startPage)
        ).data
    }

    private suspend fun getStudentDiaries() = student
        .getSchoolInfo(url.generate(UrlGenerator.Site.STUDENT) + "UczenDziennik.mvc/Get")
        .handleErrors()
        .data.orEmpty()

    private fun List<Diary>.filterDiaries() = this
        .filter { it.semesters.orEmpty().isNotEmpty() || it.kindergartenDiaryId != 0 }
        .sortedByDescending { it.level }
        .distinctBy { listOf(it.studentId, it.semesters?.firstOrNull()?.classId ?: 0) }
}
