"""Run with Python + lupa; no norns runtime required. Tests real Lua script."""
from pathlib import Path
from lupa import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(r'''
calls, polls, values, actions, definitions = {}, {}, {}, {}, {}
engine = setmetatable({}, {__index=function(_, k)
  return function(...) table.insert(calls,{k,...}) end
end})
package.preload.controlspec = function() return {new=function(a,b,w,s,d,u)
  return {min=a,max=b,default=d,units=u} end} end
package.preload.util = function() return {clamp=function(v,a,b)
  return math.max(a,math.min(v,b)) end} end
package.preload.fileselect = function() return {enter=function(_,cb)
  file_callback=cb end} end
params = {
  add_separator=function() end,
  add_control=function(_,id,name,spec)
    values[id]=spec.default; definitions[id]={name=name,spec=spec}
  end,
  set_action=function(_,id,fn) actions[id]=fn end,
  add_trigger=function() end,
  get=function(_,id) return values[id] end,
  delta=function(_,id,d)
    local s=definitions[id].spec
    values[id]=math.max(s.min,math.min(s.max,values[id]+d*0.01))
    if actions[id] then actions[id](values[id]) end
  end,
  lookup_param=function(_,id) return definitions[id] end,
  string=function(_,id) return tostring(values[id]) end
}
poll = {set=function(name)
  local p={start=function() end, stop=function(self) self.stopped=true end}
  polls[name]=p; return p
end}
metro={init=function(fn) return {start=function() end,stop=function() end} end}
screen=setmetatable({}, {__index=function() return function() end end})
_path={audio='/audio/'}
audio={file_info=function() return 2,48000,48000 end}
function count(name)
  local n=0; for _,c in ipairs(calls) do if c[1]==name then n=n+1 end end
  return n
end
function last(name)
  for i=#calls,1,-1 do if calls[i][1]==name then return calls[i] end end
end
''')
lua.execute((ROOT / "grainloom.lua").read_text())
lua.execute(r'''
init()
assert(engine.name=='Grainloom')
-- Default tape controls and no unexpected audio commands.
assert(last('loss')[2]==0.2 and last('wow')[2]==0.15)
key(2,1); key(2,1)
assert(count('capture')==1, 'duplicate record while pending')
assert(last('capture')[2]==8 and last('capture')[3]==0)
polls.grainloom_state.callback(3) -- allocating
key(2,1); assert(count('capture')==1)
polls.grainloom_state.callback(2) -- recording
key(2,1); assert(count('stopCapture')==1)
polls.grainloom_state.callback(11) -- completion revision 1
polls.grainloom_seconds.callback(3.5)
actions.resample()
assert(last('capture')[2]==3.5 and last('capture')[3]==1)
actions.resample(); assert(count('capture')==2)
polls.grainloom_state.callback(11) -- stale ready must not clear pending
actions.resample(); assert(count('capture')==2)
polls.grainloom_state.callback(13)
polls.grainloom_state.callback(12)
polls.grainloom_state.callback(21)
key(3,1); assert(last('freeze')[2]==1)
key(1,1); key(3,1); key(1,0)
assert(last('playing')[2]==0)
-- Load a sample, including cancellation and invalid metadata.
key(1,1); key(2,1); file_callback('cancel')
assert(count('read')==0)
key(1,1); key(2,1); file_callback('/audio/test.wav')
assert(last('read')[2]=='/audio/test.wav')
key(2,1); assert(count('capture')==2)
polls.grainloom_state.callback(39) -- failed operation
audio.file_info=function() return 0,0,0 end
key(1,1); key(2,1); file_callback('/audio/bad.wav')
assert(count('read')==1)
-- Recovery from failure permits fresh recording.
key(1,0); key(2,1); assert(count('capture')==3)
-- Every page is drawable and both encoders dispatch valid parameters.
for i=1,9 do enc(1,1); enc(2,1); enc(3,-1); redraw() end
cleanup()
assert(polls.grainloom_state.stopped and polls.grainloom_seconds.stopped)
''')
print("PASS: Lua controls, pending-operation races, capture, resample, file errors, cleanup")
