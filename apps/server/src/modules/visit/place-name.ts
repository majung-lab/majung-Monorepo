export type PlaceNameLookup = (lat:number,lng:number) => Promise<string|null>;

type Coord2Address = {documents?:{road_address?:{address_name?:string;building_name?:string}|null;address?:{address_name?:string}|null}[]};

/** 주소를 못 찾아도 제안은 보낼 수 있어야 하므로 실패는 null로 돌려준다. */
export function kakaoPlaceName(key=process.env.KAKAO_REST_KEY):PlaceNameLookup {
  return async (lat,lng)=>{
    if(!key) return null;
    try {
      const res=await fetch(`https://dapi.kakao.com/v2/local/geo/coord2address.json?x=${lng}&y=${lat}`,{headers:{Authorization:`KakaoAK ${key}`},signal:AbortSignal.timeout(2000)});
      if(!res.ok) return null;
      const place=((await res.json()) as Coord2Address).documents?.[0];
      const road=place?.road_address;
      if(road?.address_name) return road.building_name?`${road.building_name} (${road.address_name})`:road.address_name;
      return place?.address?.address_name??null;
    } catch {return null;}
  };
}
