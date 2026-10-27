(function(origin,user,password,submit){
 'use strict';
 origin=new URL(origin).origin;
 if(window.top!==window||location.origin!==origin)return 'wrong-origin';
 var username=document.querySelector('input[name="luci_username"]');
 var secret=document.querySelector('input[name="luci_password"][type="password"]');
 if(!username||!secret||!username.form||username.form!==secret.form)return 'not-login';
 var form=username.form,action=new URL(form.getAttribute('action')||location.href,location.href);
 if(form.method.toLowerCase()!=='post'||action.origin!==origin||!action.pathname.startsWith('/cgi-bin/luci'))return 'unsafe-form';
 if(!submit)return 'login';
 username.value=user;secret.value=password;
 username.dispatchEvent(new Event('input',{bubbles:true}));secret.dispatchEvent(new Event('input',{bubbles:true}));
 // Preserve the router's normal login form, hidden fields and CSRF values.
 var button=form.querySelector('button[type="submit"],input[type="submit"]');
 if(form.requestSubmit)form.requestSubmit(button||undefined);else if(button)button.click();else HTMLFormElement.prototype.submit.call(form);
 return 'submitted';
})(/*ARGS*/)
