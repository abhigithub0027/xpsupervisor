package com.miniaturesoftwares.xpjobssuperviser.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.google.gson.annotations.SerializedName


data class SendOtpReq(var mobile : String , var countryCode : String )
data class RegisterReq(var firstName : String , var lastName : String , var countryCode : String , var mobile : String , var email : String , var otp : String , var gender : String  )
data class LoginReq(var countryCode : String , var mobile : String , var otp : String )

data class SessionInitReq(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("environment_id") val environmentId: String,
    @SerializedName("session_start_time") val sessionStartTime: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("checksum_sha256") val checksumSha256: String,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long
)

data class SubsessionUploadReq(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("taskId") val taskId: String,
    @SerializedName("chunkId") val chunkId: String,
    @SerializedName("sequenceIndex") val sequenceIndex: Int,
    @SerializedName("s3Url") val s3Url: String,
    @SerializedName("fileSizeBytes") val fileSizeBytes: Long,
    @SerializedName("recordingDuration") val recordingDuration: Double,
    @SerializedName("createdAt") val createdAt: Double,
    @SerializedName("isPartial") val isPartial: Boolean,
    @SerializedName("totalSubsessions") val totalSubsessions: String
)

data class RecordCompleteReq(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("session_end_time") val sessionEndTime: String
)

data class DeviceDetails(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("model") val model: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("platform") val platform: String = "Android"
)

// --- Register Step 1 ---
data class RecorderRegisterReq(
    @SerializedName("full_name") val fullName: String,
    /**
     * Free-text location, prefilled by reverse geocoding and editable.
     *
     * Replaces the separate address step and its follow-up AddressReq call:
     * registration now submits one flat string, matching the iOS client.
     */
    @SerializedName("location") val location: String?,
    @SerializedName("contractor_code") val vendor: String?,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("height_cm") val heightCm: Int,
    @SerializedName("deviceBrand") val deviceBrand: String,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("device_details") val deviceDetails: DeviceDetails? = null
)

data class RecorderRegisterRes(
    @SerializedName("user_id") val userId: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

// --- Register Step 2: Address ---
data class AddressReq(
    @SerializedName("address_line_1") val addressLine1: String,
    @SerializedName("address_line_2") val addressLine2: String,
    @SerializedName("city") val city: String,
    @SerializedName("pincode") val pincode: String,
    @SerializedName("state") val state: String
)

data class AddressRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

// --- Register Step 3: KYC ---
data class KycUserProfile(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("height_cm") val heightCm: Int,
    @SerializedName("address_line_1") val addressLine1: String,
    @SerializedName("address_line_2") val addressLine2: String?,
    @SerializedName("city") val city: String,
    @SerializedName("state") val state: String,
    @SerializedName("pincode") val pincode: String,
    @SerializedName("profile_status") val profileStatus: String
)

data class KycRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: KycUserProfile
)

// --- Aadhaar & PAN KYC ---
data class AadhaarGenerateOtpReq(
    @SerializedName("aadhaar_number") val aadhaarNumber: String
)

data class AadhaarGenerateOtpRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String
)

data class AadhaarVerifyOtpReq(
    @SerializedName("aadhaar_number") val aadhaarNumber: String,
    @SerializedName("otp") val otp: String
)

data class AadhaarVerifyOtpRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String
)

data class PanVerifyReq(
    @SerializedName("pan_number") val panNumber: String
)

data class PanVerifyRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String
)

// --- Send OTP ---
data class SendRecorderOtpReq(
    @SerializedName("phone_number") val phoneNumber: String
)

data class SendRecorderOtpRes(
    @SerializedName("ok") val ok: Boolean
)

// --- Verify OTP ---
data class VerifyRecorderOtpReq(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_details") val deviceDetails: DeviceDetails? = null
)

data class VerifyRecorderOtpRes(
    @SerializedName("user_id") val userId: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String
)

// --- Error body from verify OTP ---
data class OtpErrorRes(
    @SerializedName("error") val error: String,
    @SerializedName("message") val message: String
)

// --- Notification API ---
data class NotificationListRes(
    @SerializedName("notifications") val notifications: List<com.miniaturesoftwares.xpjobssuperviser.ui.models.NotificationItem>,
    @SerializedName("pagination") val pagination: PaginationInfo
)

data class PaginationInfo(
    @SerializedName("total") val total: Int,
    @SerializedName("page") val page: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("pages") val pages: Int
)

// --- Dashboard API ---
data class DashboardRes(
    @SerializedName("contractor_code") val contractorCode: Boolean? = false,
    @SerializedName("user") val user: DashboardUser,
    @SerializedName("stats") val stats: DashboardStats,
    @SerializedName("wallet") val wallet: DashboardWallet,
    @SerializedName("sample_videos") val sampleVideos: List<SampleVideo>,
    @SerializedName("recommended_tasks") val recommendedTasks: List<TaskMapping>,
    @SerializedName("recent_sessions") val recentSessions: List<RecentSession>
)

data class Faq(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("category") val category: String
)

data class FaqRes(
    @SerializedName("faqs") val faqs: List<Faq>
)

data class DashboardUser(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("profile_status") val profileStatus: String,
    @SerializedName("contractor_code") val contractorCode: Boolean? = false
)

data class DashboardStats(
    @SerializedName("sessions_count") val sessionsCount: Int,
    @SerializedName("points_earned") val pointsEarned: Int,
    @SerializedName("uploaded_count") val uploadedCount: Int
)

data class DashboardWallet(
    @SerializedName("_id") val id: String,
    @SerializedName("balance_inr") val balanceInr: Double,
    @SerializedName("total_earned_inr") val totalEarnedInr: Double,
    @SerializedName("total_withdrawn_inr") val totalWithdrawnInr: Double,
    @SerializedName("total_tds_deducted_inr") val totalTdsDeductedInr: Double,
    @SerializedName("payout_threshold_inr") val payoutThresholdInr: Double,
    @SerializedName("reserved_inr") val reservedInr: Double? = null,
    @SerializedName("last_payout_date") val lastPayoutDate: String? = null
)

data class SampleVideo(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("category") val category: String? = null
)

data class RecommendedTask(
    @SerializedName("_id") val id: String,
    @SerializedName("task_title") val taskTitle: String,
    @SerializedName("base_pay_inr") val basePayInr: Int,
    @SerializedName("difficulty_level") val difficultyLevel: String,
    @SerializedName("task_tags") val taskTags: List<String>,
    @SerializedName("sample_video_url") val sampleVideoUrl: String?,
    @SerializedName("sample_thumbnail_url") val sampleThumbnailUrl: String?
)

data class RecentSession(
    @SerializedName("_id") val id: String,
    @SerializedName("task_id") val taskId: String,
    @SerializedName("upload_status") val uploadStatus: String,
    @SerializedName("annotation_status") val annotationStatus: String,
    @SerializedName("createdAt") val createdAt: String
)

data class GenericOkRes(
    @SerializedName("ok") val ok: Boolean
)

data class UserProfileRes(
    @SerializedName("_id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("date_of_birth") val dateOfBirth: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("height_cm") val heightCm: Int?,
    @SerializedName("language_spoken") val languageSpoken: List<String>?,
    @SerializedName("professional_tags") val professionalTags: List<String>?,
    @SerializedName("years_of_experience") val yearsOfExperience: Int?,
    @SerializedName("wallet_balance_inr") val walletBalanceInr: Double?,
    @SerializedName("profile_status") val profileStatus: String?,
    @SerializedName("address_line_1") val addressLine1: String?,
    @SerializedName("address_line_2") val addressLine2: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("pincode") val pincode: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("profession_category") val professionCategory: String?,
    @SerializedName("profession_categories") val professionCategoriesObj: ProfessionCategoriesForm?,
    @SerializedName("current_occupation") val currentOccupation: String?,
    @SerializedName("bank_name") val bankName: String?,
    @SerializedName("bank_account_last6") val bankAccountLast6: String?,
    @SerializedName("aadhaar_verified") val aadhaarVerified: Boolean?,
    @SerializedName("pan_linked") val panLinked: Boolean?,
    @SerializedName("bank_upi_details") val bankUpiDetails: Boolean?,
    @SerializedName("recorder_level") val recorderLevel: String?,
    @SerializedName("total_sessions") val totalSessions: Int?,
    @SerializedName("completed_tasks") val completedTasks: Int?,
    @SerializedName("rejected_tasks") val rejectedTasks: Int?,
    @SerializedName("approval_percentage") val approvalPercentage: Int?,
    @SerializedName("metadata") val metadata: ProfileMetadata?,
    @SerializedName("aadhaar_front_path") val aadhaarFrontPath: String?,
    @SerializedName("aadhaar_back_path") val aadhaarBackPath: String?,
    @SerializedName("pan_image_path") val panImagePath: String?
)

data class ProfileMetadata(
    @SerializedName("upi_id_masked") val upiIdMasked: String?
)

interface ApiService {

    // -------------------- AUTH --------------------
    @POST("user/send-otp")
    suspend fun sendOtp(
       @Body sendOtpReq: SendOtpReq
    ): Response<Any>

    @POST("api/v1/recorder/register")
    suspend fun registerRecorder(
        @Body body: RecorderRegisterReq
    ): Response<RecorderRegisterRes>

    @POST("api/v1/recorder/otp/send")
    suspend fun sendRecorderOtp(
        @Body body: SendRecorderOtpReq
    ): Response<SendRecorderOtpRes>

    @POST("api/v1/recorder/otp/verify")
    suspend fun verifyRecorderOtp(
        @Body body: VerifyRecorderOtpReq
    ): Response<VerifyRecorderOtpRes>

    @POST("api/v1/recorder/subsession")
    suspend fun notifySubsessionUpload(
        @Body body: SubsessionUploadReq
    ): Response<GenericOkRes>

    @POST("api/v1/record/complete")
    suspend fun completeRecording(
        @Body body: RecordCompleteReq
    ): Response<GenericOkRes>

    // -------------------- REGISTRATION STEPS --------------------
    @POST("api/v1/recorder/address")
    suspend fun submitAddress(
        @Body body: AddressReq
    ): Response<AddressRes>

    @Multipart
    @POST("api/v1/recorder/kyc")
    suspend fun uploadKyc(
        @Part aadhaarFront: MultipartBody.Part,
        @Part aadhaarBack: MultipartBody.Part
    ): Response<KycRes>

    @POST("api/v1/recorder/kyc/aadhaar/generate-otp")
    suspend fun generateAadhaarOtp(
        @Body body: AadhaarGenerateOtpReq
    ): Response<AadhaarGenerateOtpRes>

    @POST("api/v1/recorder/kyc/aadhaar/verify")
    suspend fun verifyAadhaarOtp(
        @Body body: AadhaarVerifyOtpReq
    ): Response<AadhaarVerifyOtpRes>

    @POST("api/v1/recorder/kyc/pan/verify")
    suspend fun verifyPan(
        @Body body: PanVerifyReq
    ): Response<PanVerifyRes>

    // -------------------- NOTIFICATIONS --------------------
    @GET("api/v1/recorder/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<NotificationListRes>

    @POST("api/v1/recorder/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Path("id") notificationId: String
    ): Response<GenericOkRes>

    @POST("api/v1/recorder/notifications/all/read")
    suspend fun markAllNotificationsRead(): Response<GenericOkRes>

    // -------------------- DASHBOARD --------------------
    @GET("api/v1/recorder/dashboard")
    suspend fun getDashboard(): Response<DashboardRes>

    // -------------------- TASKS --------------------
    @GET("api/v1/recorder/categories")
    suspend fun getCategories(): Response<List<com.miniaturesoftwares.xpjobssuperviser.ui.models.TaskCategory>>

    @GET("api/v1/recorder/task-category-options")
    suspend fun getTaskCategoryOptions(): Response<com.miniaturesoftwares.xpjobssuperviser.ui.models.TaskCategoryOptionsRes>

    @GET("api/v1/recorder/tasks")
    suspend fun getTasks(
        @Query("tag") tag: String? = null,
        @Query("tab") status: String? = null,
        // tag_id/tag_title are what the backend actually filters on, and what
        // iOS sends. The legacy `tag` param is kept so nothing else that calls
        // this breaks.
        @Query("tag_id") tagId: String? = null,
        @Query("tag_title") tagTitle: String? = null
    ): Response<TasksRes>

    @GET("api/v1/recorder/tasks/{id}")
    suspend fun getTaskInfo(
        @Path("id") taskId: String
    ): Response<TaskInfoRes>

    /**
     * Work-type tags, optionally filtered server-side.
     *
     * The response is an object, not a bare array - the previous
     * `Response<List<String>>` declaration could never parse it, so the tag
     * list silently fell back to "All Tasks" on every call.
     */
    @GET("api/v1/recorder/tags")
    suspend fun searchTags(
        @Query("filter") filter: String = ""
    ): Response<TaskTagsRes>

    // -------------------- WALLET --------------------
    @GET("api/v1/recorder/wallet")
    suspend fun getWallet(): Response<WalletRes>

    @POST("api/v1/recorder/wallet/bank")
    suspend fun addBank(
        @Body body: AddBankReq
    ): Response<GenericOkRes>

    @POST("api/v1/recorder/wallet/upi")
    suspend fun addUpi(
        @Body body: AddUpiReq
    ): Response<GenericOkRes>

    @Multipart
    @POST("api/v1/recorder/wallet/pan")
    suspend fun uploadPan(
        @Part panImage: MultipartBody.Part
    ): Response<PanUploadRes>

    // -------------------- LEARNING VIDEOS --------------------
    @GET("api/v1/recorder/learning-videos")
    suspend fun getLearningVideos(): Response<List<LearningVideoApi>>

    // -------------------- EARNINGS --------------------
    @GET("api/v1/recorder/earnings")
    suspend fun getEarnings(): Response<EarningsRes>

    @GET("api/v1/recorder/wallet-history")
    suspend fun getWalletHistory(): Response<WalletHistoryRes>

    // -------------------- PROFILE --------------------
    @PUT("api/v1/recorder/profile")
    suspend fun updateProfile(
        @Body body: ProfileUpdateReq
    ): Response<GenericOkRes>

    @GET("api/v1/recorder/profile")
    suspend fun getProfile(): Response<UserProfileRes>

    // -------------------- WITHDRAWAL --------------------
    @POST("api/v1/recorder/wallet/withdraw")
    suspend fun withdrawWallet(
        @Body body: WithdrawReq
    ): Response<WithdrawRes>


    @GET("api/v1/recorder/settings/help-center/contact-support")
    suspend fun getContactSupport(): Response<ContactSupportRes>

    @POST("api/v1/recorder/settings/help-center/report-issue")
    suspend fun reportIssue(
        @Body body: ReportIssueReq
    ): Response<ReportIssueRes>

    // -------------------- SAMPLE VIDEOS --------------------
    @GET("api/v1/recorder/sample-videos")
    suspend fun getSampleVideos(): Response<List<SampleVideo>>

    // -------------------- FAQ --------------------
    @GET("api/v1/recorder/settings/help-center/faqs")
    suspend fun getFaqs(): Response<FaqRes>

    // -------------------- UPLOADED SESSIONS --------------------
    @GET("api/v1/recorder/sessions")
    suspend fun getUploadedSessions(
        @Query("tab") tab: String = "uploaded"
    ): Response<UploadedSessionsRes>

    // -------------------- PROFILE DROPDOWNS --------------------

    /**
     * Occupations for the "Job" dropdown on the confirm-details screen.
     * Both of these return a bare JSON array of strings, not a wrapped object.
     */
    @GET("api/v1/app-config/occupations")
    suspend fun getOccupations(): Response<List<String>>

    /** Skill groups for the "Skill Group" dropdown on the same screen. */
    @GET("api/v1/recorder/category-meta")
    suspend fun getCategoryMeta(): Response<List<String>>

    // -------------------- DEVICE ACTIVITY --------------------

    /**
     * Tells the backend that recording has started or stopped on this operator's kit.
     * Fire-and-forget from the caller's point of view: a failure must never affect the
     * recording itself.
     */
    @POST("api/v1/recorder/device-activity")
    suspend fun postDeviceActivity(
        @Body request: DeviceActivityReq
    ): Response<DeviceActivityRes>

    // -------------------- ACCOUNT --------------------
    @DELETE("api/v1/recorder/account")
    suspend fun deleteAccount(): Response<GenericOkRes>

    @POST("api/v1/recorder/profile-form")
    suspend fun submitProfileForm(
        @Body body: ProfileFormReq
    ): Response<GenericOkRes>

}

data class ProfileFormReq(
    @SerializedName("current_occupation") val currentOccupation: String,
    @SerializedName("profession_categories") val professionCategories: ProfessionCategoriesForm,
    @SerializedName("years_of_experience") val yearsOfExperience: Int
)

data class ProfessionCategoriesForm(
    @SerializedName("profession_categories") val professionCategories: List<String>,
    @SerializedName("selected_subcategories") val selectedSubcategories: List<String>,
    @SerializedName("professional_tags") val professionalTags: List<String>
)

/**
 * Start/stop notification for a recording.
 *
 * [deviceId] is a list because a session can be recorded by several cameras at once - a
 * stereo unit plus two monos is a normal rig here - and reporting only one of them would
 * misstate which hardware produced the data.
 *
 * [sessionIds] is likewise a list: the phone records one session, but a cyber-cap splits
 * a recording into chunks that each carry their own id.
 */
data class DeviceActivityReq(
    @SerializedName("user_id") val userId: String,
    @SerializedName("device_id") val deviceId: List<String>,
    @SerializedName("task_id") val taskId: String,
    /** "active" when recording starts, "inactive" when it stops. */
    @SerializedName("recording_status") val recordingStatus: String,
    /** Identifies the phone itself, as opposed to the cameras attached to it. */
    @SerializedName("mobile_device_id") val mobileDeviceId: String,
    @SerializedName("session_ids") val sessionIds: List<String>,
    @SerializedName("location") val location: DeviceActivityLocation?
)

data class DeviceActivityLocation(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class DeviceActivityRes(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)

data class UploadedSessionsRes(
    @SerializedName("tab") val tab: String,
    @SerializedName("sessions") val sessions: List<UploadedSessionApi>
)

data class UploadedSessionApi(
    @SerializedName("_id") val id: String,
    @SerializedName("task_id") val taskId: String?,
    @SerializedName("session_start_time") val sessionStartTime: String?,
    @SerializedName("upload_status") val uploadStatus: String?,
    @SerializedName("qc_status_xp") val qcStatusXp: String?,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long?,
    @SerializedName("rejection_reason") val rejectionReason: String?,
    @SerializedName("annotation_status") val annotationStatus: String?,
    @SerializedName("task_details") val taskDetails: TaskDetailsApi?,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class TaskDetailsApi(
    @SerializedName("_id") val id: String?,
    @SerializedName("task_title") val taskTitle: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("base_pay_inr") val basePayInr: Int?
)


data class WithdrawReq(
    @SerializedName("amount_inr") val amountInr: Int,
    @SerializedName("type") val type: String
)

data class WithdrawRes(
    @SerializedName("payout_id") val payoutId: String,
    @SerializedName("status") val status: String
)

data class ProfileUpdateReq(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("height_cm") val heightCm: Int? = null,
    @SerializedName("profession_category") val professionCategory: String? = null,
    @SerializedName("years_of_experience") val yearsOfExperience: Int? = null,
    @SerializedName("professional_tags") val professionalTags: List<String>? = null,
    @SerializedName("language_spoken") val languageSpoken: List<String>? = null,
    @SerializedName("address_line_1") val addressLine1: String? = null,
    @SerializedName("address_line_2") val addressLine2: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("pincode") val pincode: String? = null,
    @SerializedName("state") val state: String? = null
)

// --- Earnings API ---
data class EarningsRes(
    @SerializedName("contractor_code") val contractorCode: Boolean? = false,
    @SerializedName("summary") val summary: EarningsSummary,
    @SerializedName("payout_setup") val payoutSetup: PayoutSetup? = null,
    @SerializedName("history") val history: List<EarningHistoryItem>
)

data class EarningsSummary(
    @SerializedName("current_balance") val currentBalance: Double,
    @SerializedName("total_earned") val totalEarned: Double,
    @SerializedName("total_tds") val totalTds: Double,
    @SerializedName("total_paid") val totalPaid: Double
)

data class WalletHistoryRes(
    @SerializedName("history") val history: List<WalletHistoryItem>
)

data class WalletHistoryItem(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String, // "earning" or "payout"
    @SerializedName("title") val title: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("gross") val gross: Double? = null,
    @SerializedName("tds") val tds: Double? = null,
    @SerializedName("status") val status: String,
    @SerializedName("date") val date: String
)

data class EarningHistoryItem(
    @SerializedName("earning_id") val earningId: String? = null,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("task_title") val taskTitle: String?,
    @SerializedName("date") val date: String,
    @SerializedName("gross") val gross: Double,
    @SerializedName("tds") val tds: Double,
    @SerializedName("net") val net: Double,
    @SerializedName("status") val status: String
)

// --- Learning Videos API ---
data class LearningVideoApi(
    @SerializedName("_id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("video_url") val videoUrl: String?,
    @SerializedName("category") val category: String,
    @SerializedName("duration_text") val durationText: String,
    @SerializedName("difficulty") val difficulty: String?,
    @SerializedName("sort_order") val sortOrder: Int?,
    @SerializedName("is_active") val isActive: Boolean?,
    @SerializedName("__v") val version: Int? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

// --- Wallet API ---
data class WalletRes(
    @SerializedName("wallet") val wallet: WalletInfo,
    @SerializedName("transactions") val transactions: List<TransactionRecord>,
    @SerializedName("payout_setup") val payoutSetup: PayoutSetup
)

data class TransactionRecord(
    @SerializedName("_id") val id: String,
    @SerializedName("wallet_id") val walletId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("type") val type: String,
    @SerializedName("amount_inr") val amountInr: Double,
    @SerializedName("balance_after_inr") val balanceAfterInr: Double,
    @SerializedName("reference_type") val referenceType: String,
    @SerializedName("reference_id") val referenceId: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class WalletInfo(
    @SerializedName("_id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("balance_inr") val balanceInr: Double,
    @SerializedName("reserved_inr") val reservedInr: Double,
    @SerializedName("total_earned_inr") val totalEarnedInr: Double,
    @SerializedName("total_withdrawn_inr") val totalWithdrawnInr: Double,
    @SerializedName("total_tds_deducted_inr") val totalTdsDeductedInr: Double,
    @SerializedName("payout_threshold_inr") val payoutThresholdInr: Double
)

data class PayoutSetup(
    @SerializedName("has_pan") val hasPan: Boolean,
    @SerializedName("has_bank") val hasBank: Boolean,
    @SerializedName("has_upi") val hasUpi: Boolean,
    @SerializedName("bank_name") val bankName: String?,
    @SerializedName("bank_account_last6") val bankAccountLast6: String?,
    @SerializedName("profile_status") val profileStatus: String?,
    @SerializedName("upi_id_masked") val upiIdMasked: String?,
    @SerializedName("full_name") val fullName: String?
)

data class AddBankReq(
    @SerializedName("account_number") val accountNumber: String,
    @SerializedName("ifsc_code") val ifscCode: String,
    @SerializedName("bank_name") val bankName: String
)

data class AddUpiReq(
    @SerializedName("upi_id") val upiId: String
)

data class PanUploadRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("paths") val paths: Map<String, String>?
)

// --- Tasks API ---
data class TasksRes(
    @SerializedName("user_id") val userId: String,
    @SerializedName("tab") val tab: String? = null,
    @SerializedName("tasks") val tasks: List<TaskMapping>
)

data class TaskMapping(
    @SerializedName("mapping_id") val mappingId: String? = null,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("task") val task: TaskDetail,
    @SerializedName("tag_overlap_score") val tagOverlapScore: String? = null,
    @SerializedName("composite_score") val compositeScore: String? = null,
    @SerializedName("matched_tags") val matchedTags: List<String>? = null,
    @SerializedName("assignment_status") val assignmentStatus: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("task_status") val status: String? = null,
    @SerializedName("submission_count") val submissionCount: Int? = null,
    @SerializedName("remaining_submissions") val remainingSubmissions: Int? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class TaskInfoRes(
    @SerializedName("_id") val id: String,
    /** See TaskDetail.metadataTemplate - the payload carries it at either level. */
    @SerializedName("meta-data") val metadataTemplate: com.google.gson.JsonObject? = null,
    @SerializedName("task") val task: TaskDetail? = null,
    @SerializedName("task_code") val taskCode: String? = null,
    @SerializedName("master_task_code") val masterTaskCode: String? = null,
    @SerializedName("task_title") val taskTitle: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("environment_id") val environmentId: String? = null,
    @SerializedName("environment_label") val environmentLabel: String? = null,
    @SerializedName("difficulty_level") val difficultyLevel: String?,
    @SerializedName("duration_minutes") val durationMinutes: Int?,
    @SerializedName("base_pay_inr") val basePayInr: Int?,
    @SerializedName("priority_level") val priorityLevel: String?,
    @SerializedName("task_tags") val taskTags: List<String>?,
    @SerializedName("max_submissions") val maxSubmissions: Int?,
    @SerializedName("current_submission_count") val currentSubmissionCount: Int?,
    @SerializedName("task_status") val taskStatus: String?,
    @SerializedName("sample_thumbnail_url") val sampleThumbnailUrl: String?,
    @SerializedName("sample_video_url") val sampleVideoUrl: String?,
    @SerializedName("recording_details") val recordingDetails: RecordingDetails?
)

data class RecordingDetails(
    @SerializedName("title") val title: String?,
    @SerializedName("subtitle") val subtitle: String?,
    @SerializedName("brief") val brief: String?,
    @SerializedName("what_you_need_to_do") val whatYouNeedToDo: List<String>?,
    @SerializedName("requirements") val requirements: List<String>?,
    @SerializedName("mistakes") val mistakes: List<String>?,
    @SerializedName("notes") val notes: String?
)

data class TaskDetail(
    @SerializedName("_id") val id: String,
    @SerializedName("task_code") val taskCode: String? = null,
    @SerializedName("master_task_code") val masterTaskCode: String? = null,
    @SerializedName("task_title") val taskTitle: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("environment_id") val environmentId: String? = null,
    @SerializedName("environment_label") val environmentLabel: String? = null,
    @SerializedName("difficulty_level") val difficultyLevel: String? = null,
    @SerializedName("priority_level") val priorityLevel: String? = null,
    @SerializedName("task_tags") val taskTags: List<String>? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("base_pay_inr") val basePayInr: Int? = null,
    @SerializedName("remaining_submissions") val max_submissions: Int? = null,
    @SerializedName("submission_count") val current_submission_count: Int? = null,
    @SerializedName("sample_video_url") val sampleVideoUrl: String? = null,
    @SerializedName("sample_thumbnail_url") val sampleThumbnailUrl: String? = null,
    @SerializedName("recording_details") val recordingDetails: RecordingDetails? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    /**
     * Backend-supplied template that drives metadata.json for this task.
     *
     * Deliberately untyped: the server owns the shape, and the iOS client
     * decodes it as an open dictionary too. Everything in here is merged into
     * the session's metadata.json, so a field added server-side reaches the
     * output without an app release.
     */
    @SerializedName("meta-data") val metadataTemplate: com.google.gson.JsonObject? = null
)

// --- Contact Support ---
data class ContactSupportRes(
    @SerializedName("contact_support") val contactSupport: ContactSupportInfo
)

// --- Report Issue ---
data class ReportIssueReq(
    @SerializedName("subject") val subject: String,
    @SerializedName("category") val category: String,
    @SerializedName("description") val description: String
)

data class ReportIssueRes(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("issue_id") val issueId: String? = null
)

data class ContactSupportInfo(
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("hours") val hours: String,
    @SerializedName("note") val note: String
)

/** One work-type tag, as returned by `api/v1/recorder/tags`. */
data class TaskTag(
    @SerializedName("id") val id: String,
    @SerializedName("work_type") val workType: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("subcategory") val subcategory: String? = null,
)

data class TaskTagsRes(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("tags") val tags: List<TaskTag> = emptyList(),
)
