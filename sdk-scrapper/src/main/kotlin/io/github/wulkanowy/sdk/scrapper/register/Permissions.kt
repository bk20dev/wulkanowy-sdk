package io.github.wulkanowy.sdk.scrapper.register

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Permissions(

    @SerialName("AuthInfos")
    val authInfos: List<AuthInfo>,

    @SerialName("Units")
    val units: List<PermissionUnit>,
)

@Serializable
internal data class AuthInfo(
    @SerialName("JednostkaSprawozdawczaId")
    val unitId: Int,

    @SerialName("LinkedAccountUids")
    val linkedAccountUids: List<Int>,

    @SerialName("LoginId")
    val loginId: Int,

    @SerialName("LoginValue")
    val loginValue: String,

    @SerialName("OpiekunIds")
    val parentIds: List<Int>,

    @SerialName("PracownikIds")
    val employeeIds: List<Int>,

    @SerialName("Roles")
    val roles: List<Int>,

    @SerialName("UczenIds")
    val studentIds: List<Int>,
)

@Serializable
internal data class PermissionUnit(
    @SerialName("Id")
    val id: Int,

    @SerialName("Nazwa")
    val name: String,

    @SerialName("Skrot")
    val short: String,

    @SerialName("Symbol")
    val symbol: String,
)
