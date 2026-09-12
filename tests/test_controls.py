"""Exercise the minimal Lua script with a mocked norns host."""
from pathlib import Path
from lupa import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(r'''
calls, polls, values, actions, definitions = {}, {}, {}, {}, {}
engine = setmetatable({}, {__index=function(_,name)
  return function(...) table.insert(calls,{name,...}) end
end})
package.preload.controlspec=function() return {new=function(a,b,w,s,d,u)
  return {min=a,max=b,default=d,units=u} end} end
package.preload.util=function() return {clamp=function(v,a,b)
  return math.max(a,math.min(v,b)) end} end
params={
  add_separator=function() end,
  add_control=function(_,id,name,spec)
    values[id]=spec.default; definitions[id]={name=name,spec=spec}
  end,
  set_action=function(_,id,fn) actions[id]=fn end,
  get=function(_,id)
    if values[id]==nil then error('invalid paramset index: '..id, 2) end
    return values[id]
  end,
  set=function(_,id,v) values[id]=v; if actions[id] then actions[id](v) end end,
  delta=function(_,id,d)
    local s=definitions[id].spec
    values[id]=math.max(s.min,math.min(s.max,values[id]+d*0.01))
    if actions[id] then actions[id](values[id]) end
  end,
  lookup_param=function(_,id) return definitions[id] end,
  string=function(_,id)
    local p=definitions[id]
    if p.formatter then return p.formatter({get=function() return values[id] end}) end
    return tostring(values[id])
  end
}
poll={set=function(name)
  local p={start=function() end,stop=function(self) self.stopped=true end}
  polls[name]=p; return p
end}
metro={init=function() return {start=function() end,stop=function() end} end}
clock_jobs={}
clock={
  run=function(fn) table.insert(clock_jobs,fn); return #clock_jobs end,
  cancel=function(id) clock_jobs[id]=nil end,
  sleep=function() end
}
function run_clocks()
  local jobs, ids = clock_jobs, {}
  clock_jobs={}
  for i in pairs(jobs) do ids[#ids+1]=i end
  table.sort(ids)
  for _,i in ipairs(ids) do jobs[i]() end
end
screen=setmetatable({texts={}}, {__index=function(t,k)
  if k=='text' or k=='text_right' then
    return function(v) table.insert(t.texts, tostring(v)) end
  end
  return function() end
end})
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
assert(last('feedback')[2]==0.72)
assert(last('rate')[2]==1)
assert(last('mix')[2]==0.8)
assert(last('sample_gain')[2]==1.5)
assert(last('gain')[2]==0.75)
assert(last('slice_size')[2]==0.2)
assert(last('slice_density')[2]==5)
assert(last('slice_speed')[2]==1.5)
assert(last('slice_reverse')[2]==0.35)
assert(last('slice_mix')[2]==0.5)
assert(last('dub_level')[2]==1)
-- New controls default to the behaviour they replaced: the whole Buffer as the
-- tape window, a uniform slice draw, and bloom fully out of the way.
assert(last('window_start')[2]==0 and last('window_size')[2]==1)
assert(last('slice_age')[2]==0 and last('slice_spread')[2]==1)
assert(last('bloom')[2]==0 and last('bloom_time')[2]==4)
assert(last('regen')[2]==0 and last('regen_tone')[2]==4000)
assert(definitions.grainloom_capture_length.spec.min==0.02)
assert(definitions.grainloom_capture_length.formatter({get=function() return 0.02 end})=='20ms')
assert(definitions.grainloom_capture_length.formatter({get=function() return 2.5 end})=='2.5s')
assert(last('liveLoop')[2]==1 and last('liveLoop')[3]==2.5)
assert(definitions.grainloom_mix.formatter({get=function() return 0 end})=='10:0')
assert(definitions.grainloom_mix.formatter({get=function() return 1 end})=='0:10')

-- Keys are ignored while the initial Buffer is allocating. K2 now carries two
-- gestures, so its freeze toggle lands on the release rather than the press.
key(2,1); key(2,0); assert(count('writing')==0)
polls.grainloom_state.callback(14)
key(2,1); key(2,0); assert(last('writing')[2]==0)
polls.grainloom_state.callback(21)
key(2,1); key(2,0); assert(last('writing')[2]==1)
key(3,1); assert(last('playing')[2]==0)
key(3,1); assert(last('playing')[2]==1)

-- Holding K2 while frozen starts dub, and that release must not also toggle.
polls.grainloom_state.callback(31)
local w = count('writing')
key(2,1); run_clocks(); assert(last('dubbing')[2]==1)
key(2,0); assert(count('writing')==w)

-- Any press during dub ends it; that release must not toggle freeze either.
polls.grainloom_state.callback(42)
key(2,1); assert(last('dubbing')[2]==0)
key(2,0); assert(count('writing')==w)

-- Holding K2 while the record head runs cannot dub: it falls back to freeze.
polls.grainloom_state.callback(54)
key(2,1); run_clocks(); assert(count('dubbing')==2)
key(2,0); assert(count('writing')==w+1)

-- Recording-time edits debounce and restart once at the final length.
params:set('grainloom_capture_length',4)
params:set('grainloom_capture_length',6)
run_clocks()
assert(count('liveLoop')==2)
assert(last('liveLoop')[2]==1 and last('liveLoop')[3]==6)

-- A resize landing mid-allocation is queued by the engine, not dropped here.
polls.grainloom_state.callback(63)
params:set('grainloom_capture_length',9)
run_clocks()
assert(count('liveLoop')==3)
assert(last('liveLoop')[2]==1 and last('liveLoop')[3]==9)

-- The watchdog releases the UI when the engine never answers the request.
local held = count('writing')
key(2,1); key(2,0); assert(count('writing')==held)
run_clocks()
key(2,1); key(2,0); assert(count('writing')==held+1)

-- All nine pages dispatch valid engine controls, starting from the first.
polls.grainloom_state.callback(73)
polls.grainloom_state.callback(84)
function page_label()
  screen.texts = {}
  redraw()
  for _,t in ipairs(screen.texts) do
    local n = t:match('^(%d+)/9 ')
    if n then return tonumber(n) end
  end
end
function goto_page1()
  for _=1,20 do
    if page_label()==1 then return end
    enc(1,1)
  end
  error('E1 never reached page 1 in 20 steps: does it wrap instead of clamp?')
end
goto_page1()
for i=1,9 do enc(2,1); enc(3,-1); redraw(); enc(1,1) end

-- E1 wraps rather than clamping, so nine pages stay reachable in both
-- directions without scrolling the whole way back.
goto_page1()
enc(1,-1); assert(page_label()==9)
enc(1,1);  assert(page_label()==1)

-- cleanup() also runs after a failed init(), where no param was ever added.
-- It must still stop the loop instead of raising on a missing paramset index.
definitions, values = {}, {}
cleanup()
assert(last('liveLoop')[2]==0)
assert(polls.grainloom_state.stopped and polls.grainloom_seconds.stopped)
''')
print("PASS: minimal loop controls, freeze/on-off keys, resize debounce,\n      queued resize, allocation watchdog,\n      K2 long-press dub,\n      window/age/bloom/regen defaults, page wrap,\n      cleanup after failed init")
