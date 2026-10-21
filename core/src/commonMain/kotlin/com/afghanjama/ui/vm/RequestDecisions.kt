package com.afghanjama.ui.vm

import com.afghanjama.data.entities.RequestTime
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo

/**
 * تأیید و ردِ درخواستِ گوشیِ کارگر — **یک در** برای صفحهٔ «کارگاهِ وصل» و
 * داشبورد.
 *
 * تأیید کارمزد و سندِ دفترِ روزنامه می‌نشاند و ساعتِ کارِ کسی را ثبت می‌کند؛
 * پس پیش از هر کاری می‌پرسد این نفر اجازه دارد ([WorkshopLinkAccess.canDecide]).
 * اگر داشبورد درِ خودش را داشت، روزی یکی از دو در این پرسش را جا می‌انداخت.
 *
 * `null` یعنی انجام شد؛ وگرنه جملهٔ دریِ دلیل.
 */
object RequestDecisions {

    suspend fun approve(repo: Repo, access: Access, r: SyncRequest, time: RequestTime = RequestTime.AUTO): String? {
        if (!WorkshopLinkAccess.canDecide(access, r.type)) return WorkshopLinkAccess.NOT_YOURS
        return repo.approveRequest(r.id, time = time)
    }

    suspend fun reject(repo: Repo, access: Access, r: SyncRequest): String? {
        if (!WorkshopLinkAccess.canDecide(access, r.type)) return WorkshopLinkAccess.NOT_YOURS
        repo.rejectRequest(r.id)
        return null
    }
}
