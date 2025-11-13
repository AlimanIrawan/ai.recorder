'use client';
import { useEffect, useRef, useState } from 'react';

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL;

export default function UploadPage() {
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [acc, setAcc] = useState('');
  const [startedAt, setStartedAt] = useState(() => new Date().toISOString());
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState('');
  const [responseHtml, setResponseHtml] = useState('');
  const fileRef = useRef(null);

  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setLat(String(pos.coords.latitude));
          setLng(String(pos.coords.longitude));
          setAcc(String(pos.coords.accuracy));
        },
        () => {}
      );
    }
  }, []);

  function onSubmit(e) {
    e.preventDefault();
    const f = fileRef.current?.files?.[0];
    if (!f || !BACKEND) return;
    const fd = new FormData();
    fd.append('file', f);
    fd.append('started_at', startedAt);
    if (lat) fd.append('latitude', lat);
    if (lng) fd.append('longitude', lng);
    if (acc) fd.append('accuracy', acc);
    setStatus('上传中');
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${BACKEND}/api/upload-audio`);
    xhr.upload.onprogress = (ev) => {
      if (ev.lengthComputable) {
        setProgress(Math.round((ev.loaded / ev.total) * 100));
      }
    };
    xhr.onreadystatechange = () => {
      if (xhr.readyState === 4) {
        setStatus(xhr.status === 200 ? '处理完成' : '失败');
        setResponseHtml(xhr.responseText || '');
      }
    };
    xhr.send(fd);
  }

  return (
    <main style={{ maxWidth: 900, margin: '24px auto', padding: '0 16px', fontFamily: '-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica,Arial,sans-serif' }}>
      <h1>上传录音</h1>
      {!BACKEND && <div style={{ color: '#b00' }}>缺少后端地址环境变量 NEXT_PUBLIC_BACKEND_URL</div>}
      <form onSubmit={onSubmit}>
        <div>
          <label>录音文件：<input ref={fileRef} type="file" accept="audio/*" required /></label>
        </div>
        <div>
          <label>开始时间（ISO）：<input value={startedAt} onChange={(e) => setStartedAt(e.target.value)} /></label>
        </div>
        <div>
          <label>纬度：<input value={lat} onChange={(e) => setLat(e.target.value)} /></label>
        </div>
        <div>
          <label>经度：<input value={lng} onChange={(e) => setLng(e.target.value)} /></label>
        </div>
        <div>
          <label>精度（米）：<input value={acc} onChange={(e) => setAcc(e.target.value)} /></label>
        </div>
        <div style={{ marginTop: 12 }}>
          <button type="submit">开始上传</button>
        </div>
      </form>
      {status && (
        <div style={{ marginTop: 16 }}>
          <div>状态：{status}</div>
          <div style={{ height: 8, background: '#eee', borderRadius: 8, overflow: 'hidden', marginTop: 8 }}>
            <div style={{ width: `${progress}%`, height: '100%', background: '#0a6' }} />
          </div>
        </div>
      )}
      {responseHtml && (
        <div style={{ marginTop: 16 }} dangerouslySetInnerHTML={{ __html: responseHtml }} />
      )}
    </main>
  );
}
