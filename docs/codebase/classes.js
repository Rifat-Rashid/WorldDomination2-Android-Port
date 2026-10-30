const $ = selector => document.querySelector(selector);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
const notes = {
  MainActivity:'Android entry point. Creates the libGDX game and forwards lifecycle and safe area information.',
  WorldDominationGame:'Owns the libGDX presentation loop, layout, drawing, and input routing.',
  GameState:'Deterministic match state. Validates choices, queues actions, and resolves turns and outcomes.',
  GameSetup:'Stores the choices used to initialize a new match.',
  CityState:'Stores population and city-level state used by turn resolution.',
  WeaponCatalog:'Defines the available weapons and their source-derived properties.',
  TechnologyCatalog:'Defines technologies, unlocks, and research properties.',
  FinalScoreBreakdown:'Holds the components of the final match score.',
  OpponentAI:'Scores options and chooses actions for computer-controlled players.',
  ScrollPaneState:'Tracks drag and scroll behavior independently for each panel.',
  BattlefieldCityLayout:'Projects source city anchors into the fitted map and hit-tests city touches.',
  MatchOutcomePresentation:'Provides display-ready match outcome information.',
  OriginalUiAssets:'Loads and maps original UI image regions for libGDX rendering.',
  OriginalAudio:'Loads, plays, and disposes the original game audio cues.'
};
let all = [], current = null, commit = '';
function source(file,line){return `https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/blob/${commit}/${encodeURI(file)}#L${line}`;}
function link(file,line,label='source'){return `<a href="${source(file,line)}" target="_blank" rel="noopener">${escapeHtml(label)} ↗</a>`;}
function entryId(entry){return `${entry.file.path}:${entry.type.line}`;}
function renderDirectory(){const q=$('#class-filter').value.trim().toLowerCase();$('#class-list').innerHTML=all.filter(entry=>(entry.type.name+' '+entry.file.package+' '+entry.file.path).toLowerCase().includes(q)).map(entry=>`<button type="button" data-id="${escapeHtml(entryId(entry))}" class="${entry===current?'selected':''}"><strong>${escapeHtml(entry.type.name)}</strong><small>${escapeHtml(entry.file.package)}</small></button>`).join('')||'<p>No matching classes.</p>';}
function variables(title,values){if(!values.length)return `<p class="empty">No ${title.toLowerCase()} declared.</p>`;return `<h4>${title}</h4><ul class="variable-list">${values.map(v=>`<li><code>${escapeHtml(v.name)}</code> <span>${escapeHtml(v.type)}</span>${v.line?` ${link(current.file.path,v.line,'L'+v.line)}`:''}</li>`).join('')}</ul>`;}
function renderMethod(method,kind){const file=current.file.path;return `<details class="method"><summary><code>${escapeHtml(method.signature)}</code><span>${escapeHtml(method.returns||kind)} · L${method.line}</span></summary><div class="method-body"><p><strong>Return:</strong> <code>${escapeHtml(method.returns||'constructor')}</code> · <strong>Access:</strong> ${escapeHtml(method.modifiers.join(' ')||'package private')} · ${link(file,method.line)}</p>${variables('Parameters',method.parameters)}${variables('Local declarations',method.locals)}<h4>Field names referenced</h4><p>${method.field_references.length?method.field_references.map(name=>`<code>${escapeHtml(name)}</code>`).join(', '):'None identified.'}</p><h4>Method calls in body</h4><p>${method.calls.length?method.calls.map(name=>`<code>${escapeHtml(name)}</code>`).join(', '):'None identified.'}</p></div></details>`;}
function renderDetail(){if(!current){$('#class-detail').innerHTML='<p>Select a class to see its members.</p>';return;}const {file,type}=current;const description=notes[type.name]||`Nested ${type.kind} declared in ${file.path.split('/').pop()}. Open its source to see its role in the containing class.`;$('#class-detail').innerHTML=`<div class="class-heading"><p class="package-name">${escapeHtml(file.package)}</p><h2>${escapeHtml(type.name)}</h2><p>${escapeHtml(description)}</p><p class="summary">${escapeHtml(type.kind)} · ${type.fields.length} fields · ${type.constructors.length} constructors · ${type.methods.length} methods · ${link(file.path,type.line,'declaration L'+type.line)}</p></div>${type.enum_values.length?`<h3>Enum values</h3><p>${type.enum_values.map(value=>`<code>${escapeHtml(value)}</code>`).join(', ')}</p>`:''}<h3>Fields</h3>${type.fields.length?`<table class="fields"><thead><tr><th>Name</th><th>Type</th><th>Access</th><th>Source</th></tr></thead><tbody>${type.fields.map(field=>`<tr><td><code>${escapeHtml(field.name)}</code></td><td><code>${escapeHtml(field.type)}</code></td><td>${escapeHtml(field.modifiers.join(' ')||'package private')}</td><td>${link(file.path,field.line,'L'+field.line)}</td></tr>`).join('')}</tbody></table>`:'<p>No fields declared.</p>'}<h3>Constructors</h3>${type.constructors.length?type.constructors.map(method=>renderMethod(method,'constructor')).join(''):'<p>No constructor declared in this type.</p>'}<h3>Methods</h3>${type.methods.length?type.methods.map(method=>renderMethod(method,'method')).join(''):'<p>No methods declared in this type.</p>'}`;}
function choose(id){current=all.find(entry=>entryId(entry)===id)||all[0]||null;renderDirectory();renderDetail();if(current)history.replaceState(null,'',`?class=${encodeURIComponent(entryId(current))}`);}
$('#class-filter').addEventListener('input',renderDirectory);
$('#class-list').addEventListener('click',event=>{const button=event.target.closest('button[data-id]');if(button)choose(button.dataset.id);});
if(window.WD2_API){const data=window.WD2_API;commit=data.commit;$('#snapshot').textContent=`Indexed ${commit.slice(0,8)}`;$('#index-commit').textContent=commit.slice(0,8);all=data.files.flatMap(file=>file.types.map(type=>({file,type})));choose(new URLSearchParams(location.search).get('class')||entryId(all[0]));}
else $('#class-detail').innerHTML='<p>The generated index is missing. Run python docs/codebase/generate.py.</p>';
