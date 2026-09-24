import type { Community, CommunityReport, Entity, GraphData, Relationship } from './graphData'

export interface EdgeData {
  caller: {
    definedInFile: string | null
    functionName: string
  }
  callee: {
    definedInFile: string | null
    functionName: string
  }
  file: string
  line: number
  indirect: boolean
  unresolved: boolean
}

export function loadEdgesData(edges: EdgeData[]): GraphData {
  const entities: Entity[] = []
  const relationships: Relationship[] = []
  const communities: Community[] = []
  const communityReports: CommunityReport[] = []

  // Create entity map to avoid duplicates
  const entityMap = new Map<string, Entity>()

  // Extract unique functions and files
  edges.forEach((edge) => {
    // Add caller entity
    const callerId = `${edge.caller.functionName}@${edge.caller.definedInFile || 'unresolved'}`
    if (!entityMap.has(callerId)) {
      entityMap.set(callerId, {
        id: callerId,
        human_readable_id: `F-${entityMap.size + 1}`,
        title: edge.caller.functionName,
        type: edge.caller.definedInFile ? 'PROCESS' : 'EVENT',
        description: edge.caller.definedInFile
          ? `Function defined in ${edge.caller.definedInFile}`
          : `Unresolved function ${edge.caller.functionName}`,
        text_unit_ids: [callerId],
        frequency: 0,
        degree: 0,
      })
    }

    // Add callee entity
    const calleeId = `${edge.callee.functionName}@${edge.callee.definedInFile || 'unresolved'}`
    if (!entityMap.has(calleeId)) {
      entityMap.set(calleeId, {
        id: calleeId,
        human_readable_id: `F-${entityMap.size + 1}`,
        title: edge.callee.functionName,
        type: edge.callee.definedInFile ? 'PROCESS' : 'EVENT',
        description: edge.callee.definedInFile
          ? `Function defined in ${edge.callee.definedInFile}`
          : `Unresolved function ${edge.callee.functionName}`,
        text_unit_ids: [calleeId],
        frequency: 0,
        degree: 0,
      })
    }
  })

  // Calculate frequency and degree
  edges.forEach((edge) => {
    const callerId = `${edge.caller.functionName}@${edge.caller.definedInFile || 'unresolved'}`
    const calleeId = `${edge.callee.functionName}@${edge.callee.definedInFile || 'unresolved'}`

    const callerEntity = entityMap.get(callerId)!
    const calleeEntity = entityMap.get(calleeId)!

    callerEntity.frequency += 1
    callerEntity.degree += 1
    calleeEntity.degree += 1
  })

  entities.push(...entityMap.values())

  // Create relationships from edges
  edges.forEach((edge, index) => {
    const callerId = `${edge.caller.functionName}@${edge.caller.definedInFile || 'unresolved'}`
    const calleeId = `${edge.callee.functionName}@${edge.callee.definedInFile || 'unresolved'}`

    const relationshipId = `rel-${index}`
    const weight = edge.unresolved ? 1 : edge.indirect ? 2 : 3

    relationships.push({
      id: relationshipId,
      human_readable_id: `R-${String(index + 1).padStart(3, '0')}`,
      source: callerId,
      target: calleeId,
      description: `${edge.caller.functionName} calls ${edge.callee.functionName} at ${edge.file}:${edge.line}`,
      weight,
      combined_degree: weight * 2,
      text_unit_ids: [`text-${relationshipId}`],
      unresolved: edge.unresolved,
    })
  })

  // Group entities by file to create communities
  const fileGroups = new Map<string, string[]>()

  entities.forEach((entity) => {
    const match = entity.description.match(/defined in (.+)$/)
    if (match) {
      const file = match[1]
      if (!fileGroups.has(file)) {
        fileGroups.set(file, [])
      }
      fileGroups.get(file)!.push(entity.id)
    }
  })

  // Create root community
  communities.push({
    id: 'community-root',
    human_readable_id: '0',
    community: '0',
    level: 0,
    children: Array.from(fileGroups.keys()).map((_, index) => String(index + 1)),
    title: 'Code Repository',
    entity_ids: entities.map((e) => e.id),
    relationship_ids: relationships.map((r) => r.id),
    text_unit_ids: ['brief-root'],
    period: 'edges',
    size: entities.length,
  })

  // Create community for each file
  Array.from(fileGroups.entries()).forEach(([file, entityIds], index) => {
    const communityId = `community-${index + 1}`
    const humanId = String(index + 1)

    // Extract file name from path
    const fileName = file.split('/').pop() || file

    // Get relationships within this community
    const relIds = relationships
      .filter((r) => entityIds.includes(r.source) && entityIds.includes(r.target))
      .map((r) => r.id)

    communities.push({
      id: communityId,
      human_readable_id: humanId,
      community: humanId,
      level: 1,
      parent: '0',
      children: [],
      title: fileName,
      entity_ids: entityIds,
      relationship_ids: relIds,
      text_unit_ids: [`brief-${humanId}`],
      period: 'edges',
      size: entityIds.length,
    })

    communityReports.push({
      id: `report-${communityId}`,
      human_readable_id: `CR-${humanId}`,
      community: humanId,
      level: 1,
      title: fileName,
      summary: `File ${file} contains ${entityIds.length} functions with ${relIds.length} internal calls`,
      full_content: `This file defines ${entityIds.length} functions and contains ${relIds.length} function calls within the same file.`,
      rank: 7,
      rank_explanation: 'Rank based on number of functions and internal calls',
      findings: [],
    })
  })

  // Create root community report
  communityReports.push({
    id: 'report-community-root',
    human_readable_id: 'CR-0',
    community: '0',
    level: 0,
    title: 'Code Repository',
    summary: `Repository contains ${entities.length} functions across ${fileGroups.size} files with ${relationships.length} function calls`,
    full_content: `This code repository has been analyzed to extract function call relationships. Total functions: ${entities.length}, Total files: ${fileGroups.size}, Total calls: ${relationships.length}`,
    rank: 10,
    rank_explanation: 'Root level repository overview',
    findings: [],
  })

  return { entities, relationships, communities, communityReports }
}
