const light = {bg:'#FFFFFF',paper:'#FFFFFF',surface:'#F6F8F5',hero:'#174F3D',onGreen:'#FFFFFF',green:'#174F3D',greenDark:'#10392D',mint:'#E9F1E9',lime:'#D9F080',text:'#1E3028',muted:'#738078',line:'#E5E9E1',orange:'#C66D30',orangeLight:'#FFF0E3',red:'#AE3E3E',dark:false};
const dark = {bg:'#101A16',paper:'#19261F',surface:'#223229',hero:'#174F3D',onGreen:'#102C20',green:'#9BD1B1',greenDark:'#10392D',mint:'#253E30',lime:'#D9F080',text:'#EEF4EC',muted:'#A9B8AD',line:'#35483C',orange:'#F1B17E',orangeLight:'#352B22',red:'#FFB3A7',dark:true};
export const C={...light};
export function applyTheme(isDark:boolean){Object.assign(C,isDark?dark:light);}
export const money = (n:number) => `₹${n.toLocaleString('en-IN',{maximumFractionDigits:2})}`;
