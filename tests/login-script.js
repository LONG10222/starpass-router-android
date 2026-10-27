'use strict';
const assert=require('assert'),fs=require('fs'),vm=require('vm'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/login.js'),'utf8');
let count=0;
function fixture(options={}) {
 const state={submitted:0,events:0};
 const form={method:options.method||'post',getAttribute:()=>options.action||'/cgi-bin/luci/admin',querySelector:()=>({type:'submit'}),requestSubmit:()=>state.submitted++};
 const user={value:'',form,dispatchEvent:()=>state.events++},password={value:'',form,dispatchEvent:()=>state.events++};
 const window={};window.top=options.iframe?{}:window;
 const context={URL,Event:class{},window,location:{origin:options.origin||'http://192.168.1.1',href:'http://192.168.1.1/cgi-bin/luci/admin'},document:{querySelector:selector=>options.noForm?null:selector.includes('luci_username')?user:password}};
 return {state,user,password,context};
}
function evaluate(f,submit=true,password='example"\\\n</script>'){return vm.runInNewContext(source.replace('/*ARGS*/',['http://192.168.1.1:80','root',password,submit].map(JSON.stringify).join(',')),f.context);}
let f=fixture();assert.equal(evaluate(f,false),'login');assert.equal(f.state.submitted,0);assert.equal(f.password.value,'');count++;
assert.equal(evaluate(f),'submitted');assert.equal(f.user.value,'root');assert.equal(f.password.value,'example"\\\n</script>');assert.equal(f.state.submitted,1);assert.equal(f.state.events,2);count++;
for(const options of [{iframe:true},{origin:'https://evil.example'},{action:'https://evil.example/cgi-bin/luci/admin'},{method:'get'},{noForm:true}]) {
 f=fixture(options);assert.notEqual(evaluate(f),'submitted');assert.equal(f.state.submitted,0);assert.equal(f.password.value,'');count++;
}
console.log('Login helper:',count,'cases passed');
