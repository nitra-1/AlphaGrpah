export interface SecurityMasterEntry {
  id: string
  symbol: string
  companyName: string
  isin: string
  listingDate: string | null
  faceValue: number | null
}

export interface Sector {
  id: string
  name: string
}

export interface Instrument {
  id: string
  symbol: string
  companyName: string
  isin: string
  sectorName: string
}
