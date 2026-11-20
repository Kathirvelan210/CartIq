import {test} from 'node:test';
import assert from 'node:assert/strict';
import {scheduleWeek} from '../src/services/weekly-recipes';

const schedule=(...ids:string[])=>scheduleWeek(ids.map(id=>({id}))).map(entry=>entry.id);

test('seven identical Jeera rice suggestions produce a single meal and leave room for other dishes',()=>{
  assert.deepEqual(schedule(...Array(7).fill('jeera-rice')),['jeera-rice']);
  assert.deepEqual(schedule(),[]);
});
test('a varied seven-day proposal keeps its original order',()=>{
  const days=['a','b','c','d','e','f','g'];
  assert.deepEqual(schedule(...days),days);
});
test('a smaller selected pool rotates fairly without adjacent repeats',()=>{
  for(let count=2;count<=6;count++){
    const ids=Array.from({length:count},(_,index)=>String(index));
    const week=schedule('0','0','0','0','0','0',...ids);
    assert.equal(week.length,7);
    assert.equal(new Set(week).size,count);
    assert.ok(week.every((id,day)=>id!==week[day-1]));
    const frequency=ids.map(id=>week.filter(pick=>pick===id).length);
    assert.ok(Math.max(...frequency)-Math.min(...frequency)<=1);
  }
});
