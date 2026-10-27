(function(){
 'use strict';
 // Match the standard DOM API on older Android System WebView builds.
 // No styles, page content, cookies or network requests are changed here.
 if(!Element.prototype.replaceChildren){
  Object.defineProperty(Element.prototype,'replaceChildren',{
   configurable:true,writable:true,value:function(){
    var fragment=this.ownerDocument.createDocumentFragment();
    for(var i=0;i<arguments.length;i++){
     var node=arguments[i];fragment.appendChild(node instanceof Node?node:this.ownerDocument.createTextNode(String(node)));
    }
    while(this.firstChild)this.removeChild(this.firstChild);
    this.appendChild(fragment);
   }
  });
 }
})();
