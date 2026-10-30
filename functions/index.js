// 카카오·네이버 로그인 토큰 확인 → Firebase 맞춤 토큰 발급 (서울 리전)
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
admin.initializeApp();

const KAKAO_APP_ID = 1630679;
const opts = { region: 'asia-northeast3', maxInstances: 10 };

async function getJson(url, token) {
  const r = await fetch(url, { headers: { Authorization: 'Bearer ' + token } });
  if (!r.ok) throw new HttpsError('unauthenticated', 'token rejected (' + r.status + ')');
  return r.json();
}

exports.kakaoLogin = onCall(opts, async (req) => {
  const token = req.data && req.data.accessToken;
  if (!token) throw new HttpsError('invalid-argument', 'accessToken');
  const info = await getJson('https://kapi.kakao.com/v1/user/access_token_info', token);
  if (info.app_id !== KAKAO_APP_ID) throw new HttpsError('permission-denied', 'wrong app');
  const me = await getJson('https://kapi.kakao.com/v2/user/me', token);
  const name = (me.kakao_account && me.kakao_account.profile && me.kakao_account.profile.nickname)
    || (me.properties && me.properties.nickname) || 'Kakao';
  const uid = 'kakao:' + me.id;
  return { token: await admin.auth().createCustomToken(uid, { provider: 'kakao' }), name };
});

exports.naverLogin = onCall(opts, async (req) => {
  const token = req.data && req.data.accessToken;
  if (!token) throw new HttpsError('invalid-argument', 'accessToken');
  const me = await getJson('https://openapi.naver.com/v1/nid/me', token);
  const p = me.response;
  if (!p || !p.id) throw new HttpsError('unauthenticated', 'no id');
  const uid = 'naver:' + p.id;
  return { token: await admin.auth().createCustomToken(uid, { provider: 'naver' }), name: p.nickname || p.name || 'Naver' };
});

// ------------------------------------------------------------------ 구독 확인 (Google Play Developer API)
const { google } = require('googleapis');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { onMessagePublished } = require('firebase-functions/v2/pubsub');
const PACKAGE = 'kr.todanjeon.phoneclean';
const PRODUCT = 'family_help_monthly';

async function play() {
  const auth = new google.auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] });
  return google.androidpublisher({ version: 'v3', auth });
}

async function applyPurchase(api, token, expectUid) {
  const r = await api.purchases.subscriptionsv2.get({ packageName: PACKAGE, token });
  const d = r.data;
  const item = (d.lineItems || []).find((x) => x.productId === PRODUCT);
  if (!item) throw new HttpsError('failed-precondition', 'product');
  const buyer = d.externalAccountIdentifiers && d.externalAccountIdentifiers.obfuscatedExternalAccountId;
  const uid = buyer || expectUid;           // 결제는 구매자 계정에 묶는다
  if (expectUid && buyer && buyer !== expectUid) throw new HttpsError('permission-denied', 'account mismatch');
  const active = ['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'].includes(d.subscriptionState);
  const expiry = new Date(item.expiryTime);
  if (active && d.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
    await api.purchases.subscriptions.acknowledge({ packageName: PACKAGE, subscriptionId: PRODUCT, token, requestBody: {} });
  }
  await admin.firestore().collection('entitlements').doc(uid).set({
    source: 'play', productId: PRODUCT, state: d.subscriptionState, purchaseToken: token,
    expiresAt: active ? admin.firestore.Timestamp.fromDate(expiry) : admin.firestore.Timestamp.fromMillis(0),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });
  return { uid, active, expiry: expiry.toISOString() };
}

exports.verifyPurchase = onCall(opts, async (req) => {
  if (!req.auth) throw new HttpsError('unauthenticated', 'login');
  const token = req.data && req.data.purchaseToken;
  if (!token) throw new HttpsError('invalid-argument', 'purchaseToken');
  const out = await applyPurchase(await play(), token, req.auth.uid);
  return { active: out.active, expiry: out.expiry };
});

// 실시간 개발자 알림(RTDN): Play Console 에서 Pub/Sub 토픽 'play-rtdn' 연결 시 갱신·취소·환불을 반영
exports.playNotifications = onMessagePublished({ topic: 'play-rtdn', region: 'asia-northeast3' }, async (event) => {
  const msg = event.data.message.json || {};
  const n = msg.subscriptionNotification;
  if (!n) return;
  await applyPurchase(await play(), n.purchaseToken, null);
});

// 90일이 지난 화면 공유 기록을 매일 지운다.
exports.cleanupSessions = onSchedule({ schedule: 'every day 04:00', timeZone: 'Asia/Seoul', region: 'asia-northeast3' }, async () => {
  const cutoff = admin.firestore.Timestamp.fromMillis(Date.now() - 90 * 24 * 3600 * 1000);
  const db = admin.firestore();
  const snap = await db.collection('sessions').where('createdAt', '<', cutoff).limit(400).get();
  for (const d of snap.docs) await db.recursiveDelete(d.ref);
});
