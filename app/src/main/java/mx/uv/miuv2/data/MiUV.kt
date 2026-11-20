package mx.uv.miuv2.data

import org.json.JSONObject

/**
 * Scripts que corren dentro del portal MiUV (dsia.uv.mx/miuv) para leer horario y credencial.
 * La sesión se abre con el formulario oficial; la app solo lee páginas, nunca modifica nada.
 */
object MiUV {
    const val LOGIN = "https://dsia.uv.mx/miuv/escritorio/login.aspx"

    fun jsLogin(user: String, pass: String) = """
        (function(){
          var u=document.getElementById('txtUser'), p=document.getElementById('txtPassword'), b=document.getElementById('btnValidacion');
          if(!u||!p||!b) return 'nologin';
          u.value=${JSONObject.quote(user)}; p.value=${JSONObject.quote(pass)}; b.click(); return 'ok';
        })()""".trimIndent()

    /** Busca un enlace o botón cuyo texto coincida y lo pulsa. */
    fun jsIrA(patron: String) = """
        (function(){
          var re=new RegExp(${JSONObject.quote(patron)},'i');
          var els=[].slice.call(document.querySelectorAll('a,button,li,span,div[onclick],input[type=button],input[type=submit]'));
          for(var i=0;i<els.length;i++){var e=els[i];var t=(e.innerText||e.value||e.title||'').trim();
            if(t.length>0&&t.length<60&&re.test(t)){e.click();return 'click:'+t;}}
          var a=[].slice.call(document.querySelectorAll('a[href]'));
          for(var j=0;j<a.length;j++){if(re.test(a[j].href)){location.href=a[j].href;return 'href:'+a[j].href;}}
          return 'none';
        })()""".trimIndent()

    /** Devuelve todas las tablas (también dentro de iframes del mismo sitio) como JSON [[[celdas]]]. */
    const val JS_TABLAS = """
        (function(){
          function tablas(doc){var out=[];[].slice.call(doc.querySelectorAll('table')).forEach(function(t){
            var filas=[];[].slice.call(t.rows).forEach(function(r){filas.push([].slice.call(r.cells).map(function(c){return (c.innerText||'').trim();}));});
            if(filas.length>1) out.push(filas);});
            [].slice.call(doc.querySelectorAll('iframe')).forEach(function(f){try{out=out.concat(tablas(f.contentDocument));}catch(e){}});
            return out;}
          return JSON.stringify(tablas(document));
        })()"""

    const val JS_TEXTO = """(function(){var t=document.body?document.body.innerText:'';
        [].slice.call(document.querySelectorAll('iframe')).forEach(function(f){try{t+='\n'+f.contentDocument.body.innerText;}catch(e){}});
        return t.slice(0,60000);})()"""

    /** Todo el texto de la página, incluido el oculto (días que la tarjeta no está mostrando), línea por nodo. */
    const val JS_TEXTO_OCULTO = """(function(){
        function txt(doc){var out=[],w=doc.createTreeWalker(doc.body,NodeFilter.SHOW_TEXT,null,false),n;
          while(n=w.nextNode()){var p=n.parentNode&&n.parentNode.nodeName;if(p==='SCRIPT'||p==='STYLE')continue;
            var s=n.nodeValue.replace(/\s+/g,' ').trim();if(s)out.push(s);}return out.join('\n');}
        var t=document.body?txt(document):'';
        [].slice.call(document.querySelectorAll('iframe')).forEach(function(f){try{t+='\n'+txt(f.contentDocument);}catch(e){}});
        return t.slice(0,80000);})()"""

    private const val JS_DIA = "/(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado)\\s+\\d{1,2}:\\d{2}/i"

    /** Marca las tarjetas de materia (el contenedor más pequeño con "Académico" y un día) y devuelve cuántas hay. */
    const val JS_MARCAR_TARJETAS = """(function(){
        var re=$JS_DIA;
        function una(e){var t=e.textContent||'';return (t.match(/acad[eé]mico/ig)||[]).length===1;}
        [].slice.call(document.querySelectorAll('[data-miuv2]')).forEach(function(e){e.removeAttribute('data-miuv2');});
        // La tarjeta es el contenedor MÁS GRANDE que todavía tiene un solo "Académico" (así incluye el título de la materia).
        var c=[].slice.call(document.querySelectorAll('div,li,section,article,td,a')).filter(function(e){
          var t=e.innerText||'';return una(e)&&re.test(t)&&!(e.parentElement&&e.parentElement!==document.body&&una(e.parentElement));});
        c.forEach(function(e,i){e.setAttribute('data-miuv2',i);});return c.length;})()"""

    /** Toca la tarjeta i (sus elementos clicables o, si no hay, la tarjeta) y devuelve su texto visible. */
    fun jsTocarTarjeta(i: Int) = """(function(){
        var c=document.querySelector('[data-miuv2="$i"]');if(!c)return '';
        var obj=[].slice.call(c.querySelectorAll('[onclick],a,button,[role=button],span,div')).filter(function(e){
          if(e.tagName==='A'){var h=e.getAttribute('href')||'';if(h&&h!=='#'&&h.indexOf('javascript')!==0)return false;}
          return e.getAttribute('onclick')||e.tagName==='A'||e.tagName==='BUTTON'||getComputedStyle(e).cursor==='pointer';});
        var blanco=obj.length?obj[obj.length-1]:c;
        try{blanco.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));}catch(e){blanco.click();}
        return '';})()"""

    fun jsTextoTarjeta(i: Int) = """(function(){var c=document.querySelector('[data-miuv2="$i"]');return c?c.innerText:'';})()"""

    /** Texto de la tarjeta i incluyendo lo oculto (solo de esa tarjeta), un nodo por línea. */
    fun jsTextoOcultoTarjeta(i: Int) = """(function(){var c=document.querySelector('[data-miuv2="$i"]');if(!c)return '';
        var out=[],w=document.createTreeWalker(c,NodeFilter.SHOW_TEXT,null,false),n;
        while(n=w.nextNode()){var s=n.nodeValue.replace(/\s+/g,' ').trim();if(s)out.push(s);}return out.join('\n');})()"""

    /** Diagnóstico: HTML de las 2 primeras tarjetas + scripts de la página (sin contraseñas ni campos ocultos). */
    const val JS_DIAGNOSTICO = """(function(){
        var out='URL: '+location.pathname+'\n';
        var cs=[].slice.call(document.querySelectorAll('[data-miuv2]')).slice(0,2);
        if(!cs.length){var e=[].slice.call(document.querySelectorAll('div')).filter(function(d){return /acad[eé]mico/i.test(d.textContent||'');});cs=e.slice(-2);}
        cs.forEach(function(c,i){var p=c.parentElement||c;out+='\n=== TARJETA '+i+' (con su contenedor) ===\n'+p.outerHTML.replace(/value="[^"]{40,}"/g,'value="…"').slice(0,15000);});
        [].slice.call(document.scripts).forEach(function(s,i){
          if(s.src){out+='\n=== SCRIPT externo: '+s.src.replace(/^.*\/\//,'')+' ===';
            if(/Estudiantes\//i.test(s.src)){try{var x=new XMLHttpRequest();x.open('GET',s.src,false);x.send();
              out+='\n'+(x.responseText||'').slice(0,30000);}catch(e){out+='\n(no se pudo leer)';}}
            return;}
          var t=s.textContent||'';if(t.length>20)out+='\n=== SCRIPT '+i+' ===\n'+t.slice(0,8000);});
        return out.slice(0,90000);})()"""

    /** Lleva la credencial a la vista y devuelve su alto en px CSS para recortar la captura. */
    const val JS_ENFOCAR_CREDENCIAL = """(function(){
        var c=[].slice.call(document.querySelectorAll('img,div,section,table')).filter(function(e){
          var t=(e.innerText||'');return /matr[ií]cula/i.test(t)&&/vigencia/i.test(t)&&e.offsetHeight>150;})
          .sort(function(a,b){return a.offsetHeight-b.offsetHeight;})[0];
        if(!c) return '';
        c.scrollIntoView({block:'start'});window.scrollBy(0,-8);
        var r=c.getBoundingClientRect();return JSON.stringify({top:r.top,height:r.height,dpr:window.devicePixelRatio});})()"""

    /**
     * Horario de szihorc.aspx: cada .divTileHr es una materia; cada .dvPanel.divCollapse01 dentro de .spDias
     * es un grupo de días (visible u oculto) con su académico, días/horas (lblCampoLunes…), edificio y salón.
     */
    const val JS_HORARIO_DOM = """(function(){
        function t(e){return e?(e.textContent||'').replace(/\s+/g,' ').trim():'';}
        var out=[];
        [].slice.call(document.querySelectorAll('.divTileHr')).forEach(function(tile){
          var mat=t(tile.querySelector('.lblEncabezadoTailHr'));if(!mat)return;
          [].slice.call(tile.querySelectorAll('.spDias .divCollapse01')).forEach(function(p){
            var prof=t(p.querySelector('[id*="lblCampoAcademico"]'));
            var edif=t(p.querySelector('[id*="lblCampoEdificio"]'));
            var salon=t(p.querySelector('[id*="lblCampoSalon"]'));
            [].slice.call(p.querySelectorAll('span[id*="lblCampo"]')).forEach(function(s){
              var m=/lblCampo(Lunes|Martes|Mi\S*rcoles|Jueves|Viernes|S\S*bado)_/i.exec(s.id);if(!m)return;
              var h=t(s.parentNode)||t(s);if(/\d{1,2}:\d{2}/.test(h))out.push({materia:mat,prof:prof,dia:m[1],hora:h,edificio:edif,salon:salon});
            });
          });
        });
        return JSON.stringify(out);})()"""

    fun clasesDom(json: String): List<Clase> {
        val a = org.json.JSONArray(json)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val d = o.getString("dia").lowercase().let {
                when { it.startsWith("lu") -> 0; it.startsWith("ma") -> 1; it.startsWith("mi") -> 2
                    it.startsWith("ju") -> 3; it.startsWith("vi") -> 4; it.startsWith("s") -> 5; else -> null } } ?: return@mapNotNull null
            val horas = Regex("""(\d{1,2})[:.](\d{2})""").findAll(o.getString("hora")).toList()
            val m = horas.firstOrNull() ?: return@mapNotNull null
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2]
            val ini = String.format(java.util.Locale.US, "%02d:%s", h, mi)
            val f = horas.getOrNull(1)
            val fin = if (f != null) String.format(java.util.Locale.US, "%02d:%s", f.groupValues[1].toInt(), f.groupValues[2])
                else String.format(java.util.Locale.US, "%02d:%s", h + 1, mi)
            val salon = listOf(o.optString("salon"), o.optString("edificio")).filter { it.isNotBlank() }.joinToString(" · ")
            Clase(d, ini, fin, o.getString("materia").toTitle(), salon, o.optString("prof").toTitle())
        }
    }

    const val JS_ES_LOGIN = "(function(){return !!document.getElementById('txtUser')})()"

    /** Saca datos de la credencial del texto visible de la página. */
    fun datosCredencial(texto: String, base: Credencial): Credencial {
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
        fun tras(re: Regex) = lineas.indexOfFirst { re.containsMatchIn(it) }.takeIf { it >= 0 }?.let { i ->
            lineas[i].substringAfter(':', "").trim().ifBlank { lineas.getOrNull(i + 1) ?: "" } } ?: ""
        val matricula = Regex("""\b[zS]{1,2}\d{8}\b""", RegexOption.IGNORE_CASE).find(texto)?.value ?: base.matricula
        return base.copy(
            matricula = matricula.uppercase().replaceFirst("ZS", "zS"),
            nombre = tras(Regex("^nombre", RegexOption.IGNORE_CASE)).ifBlank { base.nombre },
            programa = tras(Regex("programa|carrera|licenciatura", RegexOption.IGNORE_CASE)).ifBlank { base.programa },
            facultad = tras(Regex("facultad|entidad", RegexOption.IGNORE_CASE)).ifBlank { base.facultad },
            campus = tras(Regex("regi[oó]n|campus", RegexOption.IGNORE_CASE)).ifBlank { base.campus },
            vigencia = tras(Regex("vigen", RegexOption.IGNORE_CASE)).ifBlank { base.vigencia },
        )
    }
}
