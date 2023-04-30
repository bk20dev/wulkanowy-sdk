package io.github.wulkanowy.sdk.scrapper

import io.github.wulkanowy.sdk.scrapper.attendance.Absent
import io.github.wulkanowy.sdk.scrapper.attendance.Attendance
import io.github.wulkanowy.sdk.scrapper.attendance.AttendanceSummary
import io.github.wulkanowy.sdk.scrapper.attendance.Subject
import io.github.wulkanowy.sdk.scrapper.conferences.Conference
import io.github.wulkanowy.sdk.scrapper.exams.Exam
import io.github.wulkanowy.sdk.scrapper.exception.ScrapperException
import io.github.wulkanowy.sdk.scrapper.grades.GradePointsSummary
import io.github.wulkanowy.sdk.scrapper.grades.Grades
import io.github.wulkanowy.sdk.scrapper.grades.GradesStatisticsPartial
import io.github.wulkanowy.sdk.scrapper.grades.GradesStatisticsSemester
import io.github.wulkanowy.sdk.scrapper.home.DirectorInformation
import io.github.wulkanowy.sdk.scrapper.home.GovernmentUnit
import io.github.wulkanowy.sdk.scrapper.home.LuckyNumber
import io.github.wulkanowy.sdk.scrapper.homework.Homework
import io.github.wulkanowy.sdk.scrapper.login.LoginHelper
import io.github.wulkanowy.sdk.scrapper.menu.Menu
import io.github.wulkanowy.sdk.scrapper.messages.Folder
import io.github.wulkanowy.sdk.scrapper.messages.Mailbox
import io.github.wulkanowy.sdk.scrapper.messages.MessageDetails
import io.github.wulkanowy.sdk.scrapper.messages.MessageMeta
import io.github.wulkanowy.sdk.scrapper.messages.MessageReplayDetails
import io.github.wulkanowy.sdk.scrapper.messages.Recipient
import io.github.wulkanowy.sdk.scrapper.mobile.Device
import io.github.wulkanowy.sdk.scrapper.mobile.TokenResponse
import io.github.wulkanowy.sdk.scrapper.notes.Note
import io.github.wulkanowy.sdk.scrapper.register.RegisterUser
import io.github.wulkanowy.sdk.scrapper.register.Semester
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository
import io.github.wulkanowy.sdk.scrapper.repository.HomepageRepository
import io.github.wulkanowy.sdk.scrapper.repository.MessagesRepository
import io.github.wulkanowy.sdk.scrapper.repository.RegisterRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentStartRepository
import io.github.wulkanowy.sdk.scrapper.school.School
import io.github.wulkanowy.sdk.scrapper.school.Teacher
import io.github.wulkanowy.sdk.scrapper.service.ServiceManager
import io.github.wulkanowy.sdk.scrapper.student.StudentInfo
import io.github.wulkanowy.sdk.scrapper.student.StudentPhoto
import io.github.wulkanowy.sdk.scrapper.timetable.CompletedLesson
import io.github.wulkanowy.sdk.scrapper.timetable.Timetable
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URL
import java.time.LocalDate

class Scrapper {

    // TODO: refactor
    enum class LoginType {
        AUTO,
        STANDARD,
        ADFS,
        ADFSCards,
        ADFSLight,
        ADFSLightScoped,
        ADFSLightCufs,
    }

    private val changeManager = resettableManager()

    var logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var baseUrl: String = "https://fakelog.cf"
        set(value) {
            field = value
            ssl = baseUrl.startsWith("https")
            host = URL(value).let { "${it.host}:${it.port}".removeSuffix(":-1") }
        }

    var ssl: Boolean = true
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var host: String = "fakelog.cf"
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var loginType: LoginType = LoginType.AUTO
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var symbol: String = "Default"
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var email: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var password: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var schoolSymbol: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var studentId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var classId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var diaryId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var unitId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var kindergartenDiaryId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var schoolYear: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var emptyCookieJarInterceptor: Boolean = false
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var userAgentTemplate: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var androidVersion: String = "11"
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var buildTag: String = "Redmi Note 8T"
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    private val appInterceptors: MutableList<Pair<Interceptor, Boolean>> = mutableListOf()

    fun addInterceptor(interceptor: Interceptor, network: Boolean = false) {
        appInterceptors.add(interceptor to network)
    }

    private val schema by resettableLazy(changeManager) { "http" + if (ssl) "s" else "" }

    private val normalizedSymbol by resettableLazy(changeManager) { if (symbol.isBlank()) "Default" else symbol.getNormalizedSymbol() }

    private val okHttpFactory by lazy { OkHttpClientBuilderFactory() }

    private val serviceManager by resettableLazy(changeManager) {
        ServiceManager(
            okHttpClientBuilderFactory = okHttpFactory,
            logLevel = logLevel,
            loginType = loginType,
            schema = schema,
            host = host,
            symbol = normalizedSymbol,
            email = email,
            password = password,
            schoolSymbol = schoolSymbol,
            studentId = studentId,
            diaryId = diaryId,
            kindergartenDiaryId = kindergartenDiaryId,
            schoolYear = schoolYear,
            androidVersion = androidVersion,
            buildTag = buildTag,
            emptyCookieJarIntercept = emptyCookieJarInterceptor,
            userAgentTemplate = userAgentTemplate,
        ).apply {
            appInterceptors.forEach { (interceptor, isNetwork) ->
                setInterceptor(interceptor, isNetwork)
            }
        }
    }

    private val account by lazy { AccountRepository(serviceManager.getAccountService()) }

    private val register by resettableLazy(changeManager) {
        RegisterRepository(
            startSymbol = normalizedSymbol,
            email = email,
            password = password,
            loginHelper = LoginHelper(
                loginType = loginType,
                schema = schema,
                host = host,
                symbol = normalizedSymbol,
                cookies = serviceManager.getCookieManager(),
                api = serviceManager.getLoginService(),
            ),
            register = serviceManager.getRegisterService(),
            student = serviceManager.getStudentService(withLogin = false, studentInterceptor = false),
            url = serviceManager.urlGenerator,
        )
    }

    private val studentStart by resettableLazy(changeManager) {
        if (0 == studentId) throw ScrapperException("Student id is not set")
        if (0 == classId && 0 == kindergartenDiaryId) throw ScrapperException("Class id is not set")
        StudentStartRepository(
            studentId = studentId,
            classId = classId,
            unitId = unitId,
            api = serviceManager.getStudentService(withLogin = true, studentInterceptor = false),
        )
    }

    private val student by resettableLazy(changeManager) {
        StudentRepository(serviceManager.getStudentService())
    }

    private val messages by resettableLazy(changeManager) {
        MessagesRepository(serviceManager.getMessagesService())
    }

    private val homepage by resettableLazy(changeManager) {
        HomepageRepository(serviceManager.getHomepageService())
    }

    suspend fun getPasswordResetCaptcha(registerBaseUrl: String, symbol: String): Pair<String, String> = account.getPasswordResetCaptcha(registerBaseUrl, symbol)

    suspend fun sendPasswordResetRequest(registerBaseUrl: String, symbol: String, email: String, captchaCode: String): String {
        return account.sendPasswordResetRequest(registerBaseUrl, symbol, email.trim(), captchaCode)
    }

    suspend fun getUserSubjects(): RegisterUser = register.getUserSubjects()

    suspend fun getSemesters(): List<Semester> = studentStart.getSemesters()

    suspend fun getAttendance(startDate: LocalDate, endDate: LocalDate? = null): List<Attendance> {
        if (diaryId == 0) return emptyList()

        return student.getAttendance(startDate, endDate)
    }

    suspend fun getAttendanceSummary(subjectId: Int? = -1): List<AttendanceSummary> {
        if (diaryId == 0) return emptyList()

        return student.getAttendanceSummary(subjectId)
    }

    suspend fun excuseForAbsence(absents: List<Absent>, content: String? = null): Boolean = student.excuseForAbsence(absents, content)

    suspend fun getSubjects(): List<Subject> = student.getSubjects()

    suspend fun getExams(startDate: LocalDate, endDate: LocalDate? = null): List<Exam> {
        if (diaryId == 0) return emptyList()

        return student.getExams(startDate, endDate)
    }

    suspend fun getGrades(semester: Int): Grades {
        if (diaryId == 0) return Grades(
            details = emptyList(),
            summary = emptyList(),
            isAverage = false,
            isPoints = false,
            isForAdults = false,
            type = -1,
        )

        return student.getGrades(semester)
    }

    suspend fun getGradesPartialStatistics(semesterId: Int): List<GradesStatisticsPartial> {
        if (diaryId == 0) return emptyList()

        return student.getGradesPartialStatistics(semesterId)
    }

    suspend fun getGradesPointsStatistics(semesterId: Int): List<GradePointsSummary> {
        if (diaryId == 0) return emptyList()

        return student.getGradesPointsStatistics(semesterId)
    }

    suspend fun getGradesSemesterStatistics(semesterId: Int): List<GradesStatisticsSemester> {
        if (diaryId == 0) return emptyList()

        return student.getGradesAnnualStatistics(semesterId)
    }

    suspend fun getHomework(startDate: LocalDate, endDate: LocalDate? = null): List<Homework> {
        if (diaryId == 0) return emptyList()

        return student.getHomework(startDate, endDate)
    }

    suspend fun getNotes(): List<Note> = student.getNotes()

    suspend fun getConferences(): List<Conference> = student.getConferences()

    suspend fun getMenu(date: LocalDate): List<Menu> = student.getMenu(date)

    suspend fun getTimetable(startDate: LocalDate, endDate: LocalDate? = null): Timetable {
        if (diaryId == 0) return Timetable(
            headers = emptyList(),
            lessons = emptyList(),
            additional = emptyList(),
        )

        return student.getTimetable(startDate, endDate)
    }

    suspend fun getCompletedLessons(startDate: LocalDate, endDate: LocalDate? = null, subjectId: Int = -1): List<CompletedLesson> {
        if (diaryId == 0) return emptyList()

        return student.getCompletedLessons(startDate, endDate, subjectId)
    }

    suspend fun getRegisteredDevices(): List<Device> = student.getRegisteredDevices()

    suspend fun getToken(): TokenResponse = student.getToken()

    suspend fun unregisterDevice(id: Int): Boolean = student.unregisterDevice(id)

    suspend fun getTeachers(): List<Teacher> = student.getTeachers()

    suspend fun getSchool(): School = student.getSchool()

    suspend fun getStudentInfo(): StudentInfo = student.getStudentInfo()

    suspend fun getStudentPhoto(): StudentPhoto = student.getStudentPhoto()

    suspend fun getMailboxes(): List<Mailbox> = messages.getMailboxes()

    suspend fun getRecipients(mailboxKey: String): List<Recipient> = messages.getRecipients(mailboxKey)

    suspend fun getMessages(
        folder: Folder,
        mailboxKey: String? = null,
        lastMessageKey: Int = 0,
        pageSize: Int = 50,
    ): List<MessageMeta> = when (folder) {
        Folder.RECEIVED -> messages.getReceivedMessages(mailboxKey, lastMessageKey, pageSize)
        Folder.SENT -> messages.getSentMessages(mailboxKey, lastMessageKey, pageSize)
        Folder.TRASHED -> messages.getDeletedMessages(mailboxKey, lastMessageKey, pageSize)
    }

    suspend fun getReceivedMessages(mailboxKey: String? = null, lastMessageKey: Int = 0, pageSize: Int = 50): List<MessageMeta> =
        messages.getReceivedMessages(mailboxKey, lastMessageKey, pageSize)

    suspend fun getSentMessages(mailboxKey: String? = null, lastMessageKey: Int = 0, pageSize: Int = 50): List<MessageMeta> =
        messages.getSentMessages(mailboxKey, lastMessageKey, pageSize)

    suspend fun getDeletedMessages(mailboxKey: String? = null, lastMessageKey: Int = 0, pageSize: Int = 50): List<MessageMeta> =
        messages.getDeletedMessages(mailboxKey, lastMessageKey, pageSize)

    suspend fun getMessageReplayDetails(globalKey: String): MessageReplayDetails = messages.getMessageReplayDetails(globalKey)

    suspend fun getMessageDetails(globalKey: String, markAsRead: Boolean): MessageDetails = messages.getMessageDetails(globalKey, markAsRead)

    suspend fun sendMessage(subject: String, content: String, recipients: List<String>, senderMailboxId: String) =
        messages.sendMessage(subject, content, recipients, senderMailboxId)

    suspend fun deleteMessages(messagesToDelete: List<String>, removeForever: Boolean) = messages.deleteMessages(messagesToDelete, removeForever)

    suspend fun getDirectorInformation(): List<DirectorInformation> = homepage.getDirectorInformation()

    suspend fun getSelfGovernments(): List<GovernmentUnit> = homepage.getSelfGovernments()

    suspend fun getStudentThreats(): List<String> = homepage.getStudentThreats()

    suspend fun getStudentsTrips(): List<String> = homepage.getStudentsTrips()

    suspend fun getLastGrades(): List<String> = homepage.getLastGrades()

    suspend fun getFreeDays(): List<String> = homepage.getFreeDays()

    suspend fun getKidsLuckyNumbers(): List<LuckyNumber> = homepage.getKidsLuckyNumbers()

    suspend fun getKidsLessonPlan(): List<String> = homepage.getKidsLessonPlan()

    suspend fun getLastHomework(): List<String> = homepage.getLastHomework()

    suspend fun getLastTests(): List<String> = homepage.getLastTests()

    suspend fun getLastStudentLessons(): List<String> = homepage.getLastStudentLessons()
}
