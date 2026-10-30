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
