#!/usr/bin/env python3
"""LuCI-shaped local login fixture. No actual credentials or router changes."""
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import parse_qs
import html,json,secrets,threading
state={'attempts':0,'logouts':0,'token':secrets.token_hex(8),'ready':False}
lock=threading.Lock()
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def send(self,code,body=b'',headers=None):
  self.send_response(code)
  for k,v in (headers or {}).items():self.send_header(k,v)
  self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def do_GET(self):
  if self.path=='/testing/status':self.send(200,json.dumps(state).encode(),{'Content-Type':'application/json'});return
  if self.path=='/testing/expire':
   with lock:state['ready']=False;state['token']=secrets.token_hex(8)
   self.send(200,b'OK');return
  if 'logout' in self.path:
   with lock:state['logouts']+=1
  valid=state['ready'] and ('sysauth='+state['token']) in self.headers.get('Cookie','')
  if valid:
   if not self.path.startswith('/cgi-bin/luci/admin/test'):self.send(302,headers={'Location':'/cgi-bin/luci/admin/test'});return
   body='''<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><body style="font:16px sans-serif;padding:16px;background:#f6f4fb"><h1 id="welcome">路由器管理页面</h1><p>原网页正常显示</p><button onclick="document.querySelector('#state').textContent='操作成功'">测试网页按钮</button><p id="state">已登录</p><a href="/cgi-bin/luci/admin/test/network">网络菜单</a><input type="file"><p>测试服务，不连接真实路由器。</p></body></html>'''
  else:
   body='''<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><body><form method="post" action="/cgi-bin/luci/admin/test"><input name="luci_username"><input name="luci_password" type="password"><input type="hidden" name="csrf" value="keep-hidden"><button type="submit">登录</button></form></body></html>'''
  self.send(200,body.encode(),{'Content-Type':'text/html; charset=utf-8'})
 def do_POST(self):
  data=parse_qs(self.rfile.read(int(self.headers.get('Content-Length','0'))).decode())
  with lock:state['attempts']+=1
  if data.get('luci_username')==['root'] and data.get('luci_password')==['example!x&"\\示例'] and data.get('csrf')==['keep-hidden']:
   with lock:state['ready']=True
   self.send(303,headers={'Location':'/cgi-bin/luci/admin/test','Set-Cookie':'sysauth='+state['token']+'; HttpOnly; SameSite=Lax; Path=/'})
  else:self.send(303,headers={'Location':'/cgi-bin/luci/'})
if __name__=='__main__':ThreadingHTTPServer(('127.0.0.1',18086),Handler).serve_forever()
